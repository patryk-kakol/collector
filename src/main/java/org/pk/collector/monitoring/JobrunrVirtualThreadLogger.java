package org.pk.collector.monitoring;

import org.jobrunr.jobs.context.JobContext;
import org.jobrunr.server.runner.JobRunrVirtualThreadContext;
import org.jobrunr.server.runner.ThreadLocalJobContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.MDC;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * A custom SLF4J wrapper that guarantees logs are sent to the JobRunr Dashboard from both parent
 * threads and Virtual Threads, bypassing JobRunr's native wrapper bugs.
 */
public class JobrunrVirtualThreadLogger {

  private final Logger slf4jLogger;

  public JobrunrVirtualThreadLogger(Class<?> clazz) {
    this.slf4jLogger = LoggerFactory.getLogger(clazz);
  }

  public static <T> List<Callable<T>> wrapWithJobRunrContext(
      Collection<Callable<T>> tasks, JobContext context) {
    if (context == null || context == JobContext.Null) {
      return List.copyOf(tasks);
    }
    
    // Capture Mapped Diagnostic Context from the parent thread
    final Map<String, String> mdcContextMap = MDC.getCopyOfContextMap();
    
    return tasks.stream()
        .map(
            originalTask ->
                (Callable<T>)
                    () -> {
                      /* Mount the context into JobRunr's native ThreadLocal */
                      JobRunrVirtualThreadContext.set(context);
                      
                      // Restore MDC context
                      if (mdcContextMap != null) {
                          MDC.setContextMap(mdcContextMap);
                      }
                      try {
                        return originalTask.call();
                      } finally {
                        MDC.clear();
                        JobRunrVirtualThreadContext.clear();
                      }
                    })
        .toList();
  }

  /* INFO */
  public void info(String message) {
    slf4jLogger.info(message);
    logToDashboard("INFO", message);
  }

  public void info(String format, Object... arguments) {
    slf4jLogger.info(format, arguments);
    logToDashboard("INFO", MessageFormatter.arrayFormat(format, arguments).getMessage());
  }

  /* WARN */
  public void warn(String message) {
    slf4jLogger.warn(message);
    logToDashboard("WARN", message);
  }

  public void warn(String format, Object... arguments) {
    slf4jLogger.warn(format, arguments);
    logToDashboard("WARN", MessageFormatter.arrayFormat(format, arguments).getMessage());
  }

  /* ERROR */
  public void error(String message) {
    slf4jLogger.error(message);
    logToDashboard("ERROR", message);
  }

  public void error(String format, Object... arguments) {
    slf4jLogger.error(format, arguments);
    logToDashboard("ERROR", MessageFormatter.arrayFormat(format, arguments).getMessage());
  }

  public void error(String message, Throwable t) {
    slf4jLogger.error(message, t);

    StringWriter sw = new StringWriter();
    t.printStackTrace(new PrintWriter(sw));
    logToDashboard("ERROR", message + "\n" + sw);
  }

  /* DASHBOARD ROUTER */
  protected void logToDashboard(String level, String message) {
    if (ThreadLocalJobContext.hasJobContext()) {
      var jobContext = ThreadLocalJobContext.getJobContext();
      if (jobContext != null && jobContext.logger() != null) {
        var dashboardLogger = jobContext.logger();
        switch (level) {
          case "INFO" -> dashboardLogger.info(message);
          case "WARN" -> dashboardLogger.warn(message);
          case "ERROR" -> dashboardLogger.error(message);
        }
      }
    }
  }
}
