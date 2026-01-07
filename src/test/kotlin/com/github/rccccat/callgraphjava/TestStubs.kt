package com.github.rccccat.callgraphjava

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

fun CodeInsightTestFixture.addSpringAdvancedStubs() {
  addFileToProject(
      "src/org/springframework/context/annotation/Lazy.java",
      """
      package org.springframework.context.annotation;
      public @interface Lazy {
          boolean value() default true;
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/org/springframework/context/annotation/Scope.java",
      """
      package org.springframework.context.annotation;
      public @interface Scope {
          String value() default "";
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/org/springframework/context/annotation/Bean.java",
      """
      package org.springframework.context.annotation;
      public @interface Bean {
          String[] name() default {};
          String[] value() default {};
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/org/springframework/transaction/annotation/Transactional.java",
      """
      package org.springframework.transaction.annotation;
      public @interface Transactional { }
      """
          .trimIndent(),
  )
}

fun CodeInsightTestFixture.addJavaCollectionStubs() {
  addFileToProject(
      "src/java/util/List.java",
      """
      package java.util;
      public interface List<E> extends Collection<E> {
          E get(int index);
          boolean add(E e);
          void forEach(java.util.function.Consumer<? super E> action);
          java.util.stream.Stream<E> stream();
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/java/util/ArrayList.java",
      """
      package java.util;
      public class ArrayList<E> implements List<E> {
          public E get(int index) { return null; }
          public boolean add(E e) { return true; }
          public void forEach(java.util.function.Consumer<? super E> action) { }
          public java.util.stream.Stream<E> stream() { return null; }
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/java/util/Set.java",
      """
      package java.util;
      public interface Set<E> extends Collection<E> {
          boolean add(E e);
          void forEach(java.util.function.Consumer<? super E> action);
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/java/util/HashSet.java",
      """
      package java.util;
      public class HashSet<E> implements Set<E> {
          public boolean add(E e) { return true; }
          public void forEach(java.util.function.Consumer<? super E> action) { }
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/java/util/Map.java",
      """
      package java.util;
      public interface Map<K, V> {
          V get(Object key);
          V put(K key, V value);
          void forEach(java.util.function.BiConsumer<? super K, ? super V> action);
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/java/util/HashMap.java",
      """
      package java.util;
      public class HashMap<K, V> implements Map<K, V> {
          public V get(Object key) { return null; }
          public V put(K key, V value) { return null; }
          public void forEach(java.util.function.BiConsumer<? super K, ? super V> action) { }
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/java/util/Collection.java",
      """
      package java.util;
      public interface Collection<E> extends Iterable<E> {
          boolean add(E e);
          int size();
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/java/util/Optional.java",
      """
      package java.util;
      public class Optional<T> {
          public static <T> Optional<T> empty() { return new Optional<>(); }
          public static <T> Optional<T> of(T value) { return new Optional<>(); }
          public static <T> Optional<T> ofNullable(T value) { return new Optional<>(); }
          public T get() { return null; }
          public boolean isPresent() { return false; }
          public void ifPresent(java.util.function.Consumer<? super T> action) { }
          public <U> Optional<U> map(java.util.function.Function<? super T, ? extends U> mapper) { return empty(); }
          public <U> Optional<U> flatMap(java.util.function.Function<? super T, ? extends Optional<? extends U>> mapper) { return empty(); }
          public T orElse(T other) { return null; }
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/java/lang/Iterable.java",
      """
      package java.lang;
      public interface Iterable<T> {
          java.util.Iterator<T> iterator();
          default void forEach(java.util.function.Consumer<? super T> action) { }
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/java/util/Iterator.java",
      """
      package java.util;
      public interface Iterator<E> {
          boolean hasNext();
          E next();
      }
      """
          .trimIndent(),
  )
}

fun CodeInsightTestFixture.addFunctionalInterfaceStubs() {
  addFileToProject(
      "src/java/util/function/Consumer.java",
      """
      package java.util.function;
      @FunctionalInterface
      public interface Consumer<T> {
          void accept(T t);
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/java/util/function/BiConsumer.java",
      """
      package java.util.function;
      @FunctionalInterface
      public interface BiConsumer<T, U> {
          void accept(T t, U u);
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/java/util/function/Function.java",
      """
      package java.util.function;
      @FunctionalInterface
      public interface Function<T, R> {
          R apply(T t);
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/java/util/function/Supplier.java",
      """
      package java.util.function;
      @FunctionalInterface
      public interface Supplier<T> {
          T get();
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/java/util/function/Predicate.java",
      """
      package java.util.function;
      @FunctionalInterface
      public interface Predicate<T> {
          boolean test(T t);
      }
      """
          .trimIndent(),
  )
}

fun CodeInsightTestFixture.addStreamStubs() {
  addFileToProject(
      "src/java/util/stream/Stream.java",
      """
      package java.util.stream;
      public interface Stream<T> {
          Stream<T> filter(java.util.function.Predicate<? super T> predicate);
          <R> Stream<R> map(java.util.function.Function<? super T, ? extends R> mapper);
          <R, A> R collect(Collector<? super T, A, R> collector);
          void forEach(java.util.function.Consumer<? super T> action);
          T reduce(T identity, java.util.function.BinaryOperator<T> accumulator);
          java.util.Optional<T> findFirst();
          long count();
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/java/util/stream/Collector.java",
      """
      package java.util.stream;
      public interface Collector<T, A, R> { }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/java/util/stream/Collectors.java",
      """
      package java.util.stream;
      public class Collectors {
          public static <T> Collector<T, ?, java.util.List<T>> toList() { return null; }
          public static <T> Collector<T, ?, java.util.Set<T>> toSet() { return null; }
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/java/util/function/BinaryOperator.java",
      """
      package java.util.function;
      @FunctionalInterface
      public interface BinaryOperator<T> extends BiFunction<T, T, T> { }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/java/util/function/BiFunction.java",
      """
      package java.util.function;
      @FunctionalInterface
      public interface BiFunction<T, U, R> {
          R apply(T t, U u);
      }
      """
          .trimIndent(),
  )
}

fun CodeInsightTestFixture.addAutoCloseableStub() {
  addFileToProject(
      "src/java/lang/AutoCloseable.java",
      """
      package java.lang;
      public interface AutoCloseable {
          void close() throws Exception;
      }
      """
          .trimIndent(),
  )
}

fun CodeInsightTestFixture.addIntegerStub() {
  addFileToProject(
      "src/java/lang/Integer.java",
      """
      package java.lang;
      public class Integer extends Number {
          private int value;
          public Integer(int value) { this.value = value; }
          public static Integer valueOf(int i) { return new Integer(i); }
          public int intValue() { return value; }
      }
      """
          .trimIndent(),
  )
  addFileToProject(
      "src/java/lang/Number.java",
      """
      package java.lang;
      public abstract class Number {
          public abstract int intValue();
      }
      """
          .trimIndent(),
  )
}

fun CodeInsightTestFixture.addObjectStub() {
  addFileToProject(
      "src/java/lang/Object.java",
      """
      package java.lang;
      public class Object {
          public Object() { }
          public boolean equals(Object obj) { return this == obj; }
          public int hashCode() { return 0; }
          public String toString() { return ""; }
          public final Class<?> getClass() { return null; }
      }
      """
          .trimIndent(),
  )
}
