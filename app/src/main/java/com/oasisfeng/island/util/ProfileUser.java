package com.oasisfeng.island.util;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Annotation to indicate running in profile user.
 *
 * Created by Oasis on 2016/11/27.
 */
@Retention(SOURCE)
@Target({TYPE,METHOD,CONSTRUCTOR})
public @interface ProfileUser {}
