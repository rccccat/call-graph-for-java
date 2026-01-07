package com.github.rccccat.callgraphjava.util

object SpringAnnotations {
  val controllerAnnotations =
      setOf(
          "Controller",
          "RestController",
      )

  val controllerAnnotationQualifiedNames =
      listOf(
          "org.springframework.stereotype.Controller",
          "org.springframework.web.bind.annotation.RestController",
      )

  val serviceAnnotations =
      setOf(
          "Service",
          "Component",
          "Repository",
          "Configuration",
      )

  val mappingAnnotations =
      setOf(
          "RequestMapping",
          "GetMapping",
          "PostMapping",
          "PutMapping",
          "DeleteMapping",
          "PatchMapping",
      )

  val injectionAnnotations =
      setOf(
          "Autowired",
          "Inject",
          "Resource",
      )

  val qualifierAnnotations =
      setOf(
          "Qualifier",
          "Named",
      )

  val resourceAnnotations =
      setOf(
          "Resource",
      )

  val primaryAnnotations =
      setOf(
          "Primary",
      )

  val componentAnnotations = controllerAnnotations + serviceAnnotations
}

object MyBatisAnnotations {
  val mapperAnnotations =
      setOf(
          "Mapper",
          "Repository",
          "MapperScan",
      )

  val mapperAnnotationQualifiedNames =
      listOf(
          "org.apache.ibatis.annotations.Mapper",
          "org.springframework.stereotype.Repository",
          "org.mybatis.spring.annotation.MapperScan",
      )

  val sqlAnnotations =
      setOf(
          "Select",
          "Insert",
          "Update",
          "Delete",
      )
}
