package com.github.rccccat.ideacallgraph

import com.intellij.testFramework.fixtures.CodeInsightTestFixture

fun CodeInsightTestFixture.addSpringCoreStubs() {
  addFileToProject(
      "src/org/springframework/beans/factory/annotation/Autowired.java",
      """
      package org.springframework.beans.factory.annotation;
      public @interface Autowired { }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/org/springframework/beans/factory/annotation/Qualifier.java",
      """
      package org.springframework.beans.factory.annotation;
      public @interface Qualifier {
          String value() default "";
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/org/springframework/context/annotation/Primary.java",
      """
      package org.springframework.context.annotation;
      public @interface Primary { }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/org/springframework/stereotype/Service.java",
      """
      package org.springframework.stereotype;
      public @interface Service { }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/org/springframework/stereotype/Repository.java",
      """
      package org.springframework.stereotype;
      public @interface Repository { }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/org/springframework/stereotype/Component.java",
      """
      package org.springframework.stereotype;
      public @interface Component { }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/org/springframework/stereotype/Controller.java",
      """
      package org.springframework.stereotype;
      public @interface Controller { }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/org/springframework/context/annotation/Configuration.java",
      """
      package org.springframework.context.annotation;
      public @interface Configuration { }
      """
          .trimIndent(),
  )
}

fun CodeInsightTestFixture.addSpringWebStubs() {
  addFileToProject(
      "src/org/springframework/web/bind/annotation/RestController.java",
      """
      package org.springframework.web.bind.annotation;
      public @interface RestController { }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/org/springframework/web/bind/annotation/RequestMethod.java",
      """
      package org.springframework.web.bind.annotation;
      public enum RequestMethod { GET, POST, PUT, DELETE, PATCH }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/org/springframework/web/bind/annotation/RequestMapping.java",
      """
      package org.springframework.web.bind.annotation;
      public @interface RequestMapping {
          String value() default "";
          String path() default "";
          RequestMethod[] method() default {};
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/org/springframework/web/bind/annotation/GetMapping.java",
      """
      package org.springframework.web.bind.annotation;
      public @interface GetMapping {
          String value() default "";
          String path() default "";
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/org/springframework/web/bind/annotation/PostMapping.java",
      """
      package org.springframework.web.bind.annotation;
      public @interface PostMapping {
          String value() default "";
          String path() default "";
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/org/springframework/web/bind/annotation/PutMapping.java",
      """
      package org.springframework.web.bind.annotation;
      public @interface PutMapping {
          String value() default "";
          String path() default "";
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/org/springframework/web/bind/annotation/DeleteMapping.java",
      """
      package org.springframework.web.bind.annotation;
      public @interface DeleteMapping {
          String value() default "";
          String path() default "";
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/org/springframework/web/bind/annotation/PatchMapping.java",
      """
      package org.springframework.web.bind.annotation;
      public @interface PatchMapping {
          String value() default "";
          String path() default "";
      }
      """
          .trimIndent(),
  )
}

fun CodeInsightTestFixture.addMyBatisStubs() {
  addFileToProject(
      "src/org/apache/ibatis/annotations/Mapper.java",
      """
      package org.apache.ibatis.annotations;
      public @interface Mapper { }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/org/apache/ibatis/annotations/Select.java",
      """
      package org.apache.ibatis.annotations;
      public @interface Select {
          String value();
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/org/apache/ibatis/annotations/Insert.java",
      """
      package org.apache.ibatis.annotations;
      public @interface Insert {
          String value();
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/org/apache/ibatis/annotations/Update.java",
      """
      package org.apache.ibatis.annotations;
      public @interface Update {
          String value();
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/org/apache/ibatis/annotations/Delete.java",
      """
      package org.apache.ibatis.annotations;
      public @interface Delete {
          String value();
      }
      """
          .trimIndent(),
  )
}

fun CodeInsightTestFixture.addJavaReflectionStubs() {
  addFileToProject(
      "src/java/lang/Class.java",
      """
      package java.lang;
      public class Class<T> {
          public java.lang.reflect.Method getDeclaredMethod(String name, Class<?>... parameterTypes) { return null; }
          public java.lang.reflect.Method getMethod(String name, Class<?>... parameterTypes) { return null; }
          public T newInstance() { return null; }
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/java/lang/reflect/Method.java",
      """
      package java.lang.reflect;
      public class Method {
          public Object invoke(Object obj, Object... args) { return null; }
      }
      """
          .trimIndent(),
  )
}
