package io.github.goooler.exporter

import io.github.goooler.exporter.ArrayRes.Companion.map
import io.github.goooler.exporter.PluralsRes.Companion.map
import io.github.goooler.exporter.StringRes.Companion.map
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.outputStream
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.jdom2.Element
import org.jdom2.input.SAXBuilder

fun res2xls(inputPath: String, outputPath: String) {
  val workbook = HSSFWorkbook()
  val stringSheet = workbook.createSheet(StringRes.TAG).apply {
    val firstRow = createRow(0)
    firstRow.createCell(0).setCellValue("key")
    firstRow.createCell(1).setCellValue("translatable")
  }
  val pluralsSheet = workbook.createSheet(PluralsRes.TAG).apply {
    val firstRow = createRow(0)
    firstRow.createCell(0).setCellValue("key")
    firstRow.createCell(1).setCellValue("translatable")
    firstRow.createCell(2).setCellValue("quantity")
  }
  val arraySheet = workbook.createSheet(ArrayRes.TAG).apply {
    val firstRow = createRow(0)
    firstRow.createCell(0).setCellValue("key")
    firstRow.createCell(1).setCellValue("translatable")
  }

  val defaultStringColumn: ResColumn<StringRes> = mutableMapOf()
  val defaultPluralsColumn: ResColumn<PluralsRes> = mutableMapOf()
  val defaultArrayColumn: ResColumn<ArrayRes> = mutableMapOf()
  val stringColumns = mutableListOf<ResColumn<StringRes>>()
  val pluralsColumns = mutableListOf<ResColumn<PluralsRes>>()
  val arrayColumns = mutableListOf<ResColumn<ArrayRes>>()

  parseResFiles(inputPath).forEachIndexed { index, path ->
    val elements = SAXBuilder().build(path.inputStream()).rootElement.children
    val folderName = path.parent.name
    val (first, second, third) = if (folderName == "values") {
      fillNewColumn(true, elements, defaultStringColumn, defaultPluralsColumn, defaultArrayColumn)
    } else {
      val newStringColumn = defaultStringColumn.mapValues { it.value.map() }.toMutableMap()
      val newPluralsColumn = defaultPluralsColumn.mapValues { it.value.map() }.toMutableMap()
      val newArrayColumn = defaultArrayColumn.mapValues { it.value.map() }.toMutableMap()
      fillNewColumn(false, elements, newStringColumn, newPluralsColumn, newArrayColumn)
    }
    stringColumns += first
    pluralsColumns += second
    arrayColumns += third
    // key, translatable, value, value-zh-rCN...
    stringSheet.first().createCell(index + 2).setCellValue(folderName)
    // key, translatable, quantity, value, value-zh-rCN...
    pluralsSheet.first().createCell(index + 3).setCellValue(folderName)
    // key, translatable, value, value-zh-rCN...
    arraySheet.first().createCell(index + 2).setCellValue(folderName)
  }

  stringColumns.forEachIndexed { columnIndex, column ->
    column.values.forEachIndexed { rowIndex, stringRes ->
      val sheetRowIndex = rowIndex + 1
      if (columnIndex == 0) {
        val key = stringRes.name
        check(key.isNotEmpty()) { "Default string res keys can't be null" }
        stringSheet.createRow(sheetRowIndex).also {
          it.createCell(0).setCellValue(key)
          it.createCell(1).setCellValue(if (stringRes.translatable) "" else "false")
        }
      }
      stringSheet.getRow(sheetRowIndex).createCell(columnIndex + 2)
        .setCellValue(stringRes.value)
    }
  }

  pluralsColumns.forEachIndexed { columnIndex, column ->
    column.values.forEachIndexed { rowIndex, pluralsRes ->
      val pluralsValues = pluralsRes.values
      val start = rowIndex * pluralsValues.size + 1
      val end = start + pluralsValues.size
      for (i in start until end) {
        val row = pluralsSheet.getRow(i) ?: pluralsSheet.createRow(i)
        if (columnIndex == 0) {
          check(pluralsValues.isNotEmpty()) { "Default plurals res values can't be null" }
          // Write key only once for a plurals res.
          if (i == start) {
            row.createCell(0).setCellValue(pluralsRes.name)
            row.createCell(1).setCellValue(if (pluralsRes.translatable) "" else "false")
          } else {
            row.createCell(0).setCellValue("")
            row.createCell(1).setCellValue("")
          }
          val quantity = pluralsValues.entries.toList()[i - start]
          row.createCell(2).setCellValue(quantity.key)
          row.createCell(3).setCellValue(quantity.value)
        } else {
          val value = pluralsValues.values.toList()[i - start]
          row.createCell(columnIndex + 3).setCellValue(value)
        }
      }
    }
  }

  arrayColumns.forEachIndexed { columnIndex, column ->
    // Starts from 1 to skip the title row.
    var lastArrayIndex = 1
    column.values.forEachIndexed { rowIndex, arrayRes ->
      val arrayValues = arrayRes.values
      val start = rowIndex + lastArrayIndex
      val end = start + arrayValues.size
      for (i in start until end) {
        val row = arraySheet.getRow(i) ?: arraySheet.createRow(i)
        if (columnIndex == 0) {
          check(arrayValues.isNotEmpty()) { "Default array res values can't be null" }
          // Write key only once for an array res.
          if (i == start) {
            row.createCell(0).setCellValue(arrayRes.name)
            row.createCell(1).setCellValue(if (arrayRes.translatable) "" else "false")
          } else {
            row.createCell(0).setCellValue("")
            row.createCell(1).setCellValue("")
          }
          row.createCell(2).setCellValue(arrayValues[i - start])
        } else {
          val value = arrayValues[i - start]
          row.createCell(columnIndex + 2).setCellValue(value)
        }
      }
      lastArrayIndex = end - 1
    }
  }

  val path = Paths.get(outputPath, "output.xls")
  path.outputStream().use { os ->
    workbook.use { it.write(os) }
  }

  outputInfo("$SUCCESS_OUTPUT ${path.normalize()}")
}

internal fun Element.toStringResOrNull(translatable: Boolean): StringRes? {
  if (name != "string") return null
  val key = getAttributeValue("name") ?: return null
  return StringRes(
    name = key,
    translatable = translatable,
    value = text,
  )
}

internal fun Element.toPluralsResOrNull(translatable: Boolean): PluralsRes? {
  if (name != "plurals") return null
  val key = getAttributeValue("name") ?: return null
  val pluralsRes = PluralsRes(key, translatable)
  children.forEach {
    val quantity = it.getAttributeValue("quantity") ?: return@forEach
    pluralsRes.values[quantity] = it.text
  }
  return pluralsRes
}

internal fun Element.toArrayResOrNull(translatable: Boolean): ArrayRes? {
  if (name != "array" && name != "string-array") return null
  val key = getAttributeValue("name") ?: return null
  val items = children.map { it.text }
  return ArrayRes(key, translatable, items)
}

internal fun Element.toTextResOrNull(): TextRes? {
  val translatable = getAttributeValue("translatable") != "false"
  return toStringResOrNull(translatable) ?: toPluralsResOrNull(translatable) ?: toArrayResOrNull(translatable)
}

internal fun parseResFiles(resRoot: String, resFile: String = "strings.xml"): Sequence<Path> {
  return Paths.get(resRoot).listDirectoryEntries("values*").asSequence()
    .sorted()
    .map { it.resolve(resFile) }
    .filter { it.isRegularFile() && it.exists() }
}

private fun fillNewColumn(
  fillDefault: Boolean,
  elements: List<Element>,
  stringColumn: ResColumn<StringRes>,
  pluralsColumn: ResColumn<PluralsRes>,
  arrayColumn: ResColumn<ArrayRes>,
): Triple<ResColumn<StringRes>, ResColumn<PluralsRes>, ResColumn<ArrayRes>> {
  elements.forEach { element ->
    val res = element.toTextResOrNull() ?: return@forEach
    when (res) {
      is StringRes -> {
        if (fillDefault || stringColumn.containsKey(res.name)) {
          stringColumn[res.name] = res
        }
      }
      is PluralsRes -> {
        if (fillDefault || pluralsColumn.containsKey(res.name)) {
          pluralsColumn[res.name] = res
        }
      }
      is ArrayRes -> {
        if (fillDefault || arrayColumn.containsKey(res.name)) {
          val standard = arrayColumn[res.name]?.values
          val filled = if (standard != null) {
            (res.values + standard).subList(0, standard.size)
          } else {
            res.values
          }
          arrayColumn[res.name] = res.copy(values = filled)
        }
      }
    }
  }
  return Triple(stringColumn, pluralsColumn, arrayColumn)
}

private typealias ResColumn<T> = MutableMap<String, T>
