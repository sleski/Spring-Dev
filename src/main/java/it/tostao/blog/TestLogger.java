package it.tostao.blog;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestLogger.class);

    @Test
    public void testowa() {

        LOGGER.info("111");
        LOGGER.trace("111");
        LOGGER.debug("111");
        LOGGER.error("111");
        LOGGER.warn("111");
    }
}
