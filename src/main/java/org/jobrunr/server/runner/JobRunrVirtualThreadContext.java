package org.jobrunr.server.runner;

import org.jobrunr.jobs.context.JobContext;
import java.lang.reflect.Method;

/**
 * A package-private bridge that allows us to explicitly mount JobRunr's native context onto custom
 * Virtual Threads, bypassing SLF4J/Log4j2 appender isolation.
 */
public final class JobRunrVirtualThreadContext {

  private static final Method SET_METHOD;
  private static final Method CLEAR_METHOD;

  static {
    try {
      /* Find the package-private methods inside JobRunr */
      SET_METHOD = ThreadLocalJobContext.class.getDeclaredMethod("setJobContext", JobContext.class);
      CLEAR_METHOD = ThreadLocalJobContext.class.getDeclaredMethod("clear");

      /* Forcefully override Java's visibility rules (bypasses the IllegalAccessError) */
      SET_METHOD.setAccessible(true);
      CLEAR_METHOD.setAccessible(true);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("Failed to initialize JobRunr reflection bridge", e);
    }
  }

  private JobRunrVirtualThreadContext() {}

  public static void set(JobContext context) {
    if (context != null && context != JobContext.Null) {
      try {
        SET_METHOD.invoke(null, context);
      } catch (Exception e) {
        System.err.println("Failed to set JobRunr context via reflection: " + e.getMessage());
      }
    }
  }

  public static void clear() {
    try {
      CLEAR_METHOD.invoke(null);
    } catch (Exception e) {
      System.err.println("Failed to clear JobRunr context via reflection: " + e.getMessage());
    }
  }
}
