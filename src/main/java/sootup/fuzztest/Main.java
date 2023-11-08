package sootup.fuzztest;


import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sootup.core.model.SootClass;
import sootup.core.model.SourceType;
import sootup.java.bytecode.inputlocation.BytecodeClassLoadingOptions;
import sootup.java.bytecode.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.core.JavaProject;
import sootup.java.core.language.JavaLanguage;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;

public class Main {
  final static Logger logger = LoggerFactory.getLogger(Main.class);
  // To get an accurate performance data, we will limit the number of threads to cpu number
  // if any thread timeout, the next test will be give up (if exitOnTimeout = true)
  // because this thread may be running infinitely, it can not be reused in the next test
  final static int batchSize = Runtime.getRuntime().availableProcessors() - 1;
  // WARNING: if you set this to false, the thread number may increase to a very large number
  final static boolean exitOnTimeout = false;
  final static ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newCachedThreadPool();

  static boolean checkFutures(List<FuzzTask> tasks, List<Future> futures) {
    boolean isTimeout = true;
    for (int i = 0; i < futures.size(); i++) {
      try {
        futures.get(i).get(10, TimeUnit.SECONDS);
      } catch (InterruptedException | TimeoutException e) {
        isTimeout = false;
        logger.error("run task {} timeout, current thread pool size {}", tasks.get(i), pool.getPoolSize());
      } catch (Exception e) {
        logger.error("run task {} failed", tasks.get(i), e);
      }
    }
    return isTimeout;
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
      // to get a stable result
      var classes = new ArrayList<>(view.getClasses().stream().toList());
      classes.sort(Comparator.comparing(SootClass::getName));
      List<Future> futures = new ArrayList<>();
      List<FuzzTask> tasks = new ArrayList<>();
      for (var clazz : classes) {
        var task = new FuzzTask(file.getPath(), clazz);
        tasks.add(task);
        futures.add(pool.submit(task));
        if (futures.size() == batchSize) {
          if (!checkFutures(tasks, futures)) {
            if (exitOnTimeout) {
              System.exit(1);
            }
          }
          futures.clear();
          tasks.clear();
        }
      }
      if (!checkFutures(tasks, futures)) {
        if (exitOnTimeout) {
          System.exit(1);
        }
      }
    }
    logger.info("test done");
    pool.shutdown();
    // System.exit(0);
  }
}
