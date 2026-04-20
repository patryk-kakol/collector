package org.pk.collector.monitoring;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.jobrunr.server.runner.ThreadLocalJobContext;

import java.io.Serial;
import java.io.Serializable;

@Plugin(
        name = "JobRunrAppender",
        category = Core.CATEGORY_NAME,
        elementType = Appender.ELEMENT_TYPE,
        printObject = true
)
public class JobRunrAppender extends AbstractAppender implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    protected JobRunrAppender(String name, Filter filter) {
        super(name, filter, PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY);
    }

    @PluginFactory
    public static JobRunrAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter) {
        return new JobRunrAppender(name, filter);
    }

    @Override
    public void append(LogEvent event) {
        // Only route logs when inside a JobRunr job context
        if (ThreadLocalJobContext.hasJobContext()) {
            try {
                var dashboardLogger = ThreadLocalJobContext.getJobContext().logger();
                String message = getLayout().toSerializable(event).toString();

                switch (event.getLevel().getStandardLevel()) {
                    case WARN:
                        dashboardLogger.warn(message);
                        break;
                    case ERROR:
                    case FATAL:
                        dashboardLogger.error(message);
                        break;
                    case INFO:
                    default:
                        dashboardLogger.info(message);
                }
            } catch (Exception e) {
                error("Error routing log to JobRunr", event, e);
            }
        }
    }

    @Override
    public boolean stop(long timeout, java.util.concurrent.TimeUnit timeUnit) {
        setStopping();
        boolean stopped = super.stop(timeout, timeUnit);
        setStopped();
        return stopped;
    }
}
