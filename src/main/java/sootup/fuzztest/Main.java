package sootup.fuzztest;


import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sootup.core.model.SourceType;
import sootup.java.bytecode.inputlocation.BytecodeClassLoadingOptions;
import sootup.java.bytecode.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.core.JavaProject;
import sootup.java.core.language.JavaLanguage;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;

public class Main {
  final static Logger logger = LoggerFactory.getLogger(Main.class);
  final static int batchSize = Runtime.getRuntime().availableProcessors() - 1;
  // To get an accurate performance data, we will limit the number of threads to cpu number
  // if any thread timeout, the next test will be give up, because this thread may be running infinitely
  // it can not be reused in the next test
  final static ExecutorService pool = Executors.newFixedThreadPool(batchSize);

  static boolean checkFutures(List<FuzzTask> tasks, List<Future> futures) {
    boolean ret = true;
    for (int i = 0; i < futures.size(); i++) {
      try {
        futures.get(i).get(10, TimeUnit.SECONDS);
      } catch (InterruptedException | TimeoutException e) {
        ret = false;
        logger.error("run task {} timeout", tasks.get(i));
      } catch (Exception e) {
        logger.error("run task {} failed", tasks.get(i), e);
      }
    }
    return ret;
  }

  public static void main(String[] args) throws InterruptedException {
    // TODO: add jmod
    Iterator<File> iter = FileUtils.iterateFiles(new File("./data"), new String[]{"jar"}, true);
    while (iter.hasNext()) {
      var file = iter.next();
      var project = JavaProject.builder(new JavaLanguage(8)).addInputLocation(
              new JavaClassPathAnalysisInputLocation(file.getPath(), SourceType.Application)).build();
      var view = project.createView();
      view.configBodyInterceptors((l) -> BytecodeClassLoadingOptions.Default);
      var classes = view.getClasses();
      var it = classes.iterator();
      List<Future> futures = new ArrayList<>();
      List<FuzzTask> tasks = new ArrayList<>();
      while (it.hasNext()) {
        var task = new FuzzTask(file.getPath(), it.next());
        tasks.add(task);
        futures.add(pool.submit(task));
        if (futures.size() == batchSize) {
          if (!checkFutures(tasks, futures)) {
            // we cannot wait the thread to exit, because it may be running infinitely
            System.exit(1);
          }
          futures.clear();
          tasks.clear();
        }
      }
      checkFutures(tasks, futures);
    }
    logger.info("test done");
    System.exit(0);
  }
}
