package me.wiefferink.errorsink.common;

import com.google.common.collect.Lists;
import ninja.leaping.configurate.ConfigurationNode;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommonUtils {

    public static boolean hasOldLog4j2;
    public static int messagesSent = 0;


    public static void initialzeMatchers() {

    }
}
