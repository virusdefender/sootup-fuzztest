package sootup.fuzztest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sootup.core.model.SootClass;

import java.util.concurrent.Callable;

public class FuzzTask implements Callable<Void> {
  final static Logger logger = LoggerFactory.getLogger(FuzzTask.class);
  String jar;
  SootClass<?> sootClass;

  FuzzTask(String jar, SootClass<?> sootClass) {
    this.jar = jar;
    this.sootClass = sootClass;
  }


  @Override
  public Void call() {
    // to identify the logs
    Thread.currentThread().setName(toString());
    for (var method : sootClass.getMethods()) {
      var start = System.currentTimeMillis();
      logger.debug("start");
      if (method.hasBody()) {
        method.getBody().getStmts();
      }
      long timeCost = System.currentTimeMillis() - start;
      if (timeCost < 200) {
        logger.debug("done, time cost {}", timeCost);
      } else {
        logger.warn("done, time cost {}ms", timeCost);
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return String.format("%s:%s", jar, sootClass.getName());
  }
}
