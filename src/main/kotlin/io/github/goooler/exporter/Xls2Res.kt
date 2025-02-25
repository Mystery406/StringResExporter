package io.github.goooler.exporter

import java.io.IOException
import java.nio.file.Paths
import kotlin.io.path.createParentDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter

fun xls2res(inputPath: String, outputPath: String) {
  val workbook = try {
    WorkbookFactory.create(Paths.get(inputPath).inputStream())
  } catch (_: IOException) {
    // This is a trade-off for Jar size, see https://github.com/Goooler/StringResExporter/pull/23.
    outputError("We support XLS file only, invalid format in $inputPath")
  }
  writeStrings(workbook, outputPath)
  writePlurals(workbook, outputPath)
  writeArray(workbook, outputPath)
  outputInfo("$SUCCESS_OUTPUT ${Paths.get(outputPath).normalize()}")
}

internal fun writeStrings(workbook: Workbook, outputPath: String) {
  val stringSheet = workbook.getSheet(StringRes.TAG) ?: run {
    outputWarn("Sheet ${StringRes.TAG} not found.")
    return
  }
  val stringResMap = mutableMapOf<String, MutableList<StringRes>>()

  stringSheet.rowIterator().asSequence().drop(1).forEach { row ->
    if (row.isEmpty()) return@forEach
    val key = row.getCell(0).stringValue
    val translatable = row.getCell(1).stringValue != "false"
    row.cellIterator().asSequence().drop(2).forEachIndexed { index, cell ->
      val folderName = stringSheet.getRow(0).getCell(index + 2).stringValue
      val value = cell.stringValue
      stringResMap.getOrPut(folderName) {
        mutableListOf()
      }.add(StringRes(key, translatable, value))
    }
  }

  stringResMap.forEach { (folderName, stringResList) ->
    val rootElement = Element("resources")
    val elements = stringResList.asSequence()
      .filter { it.value.isNotEmpty() }
      .map {
        Element("string").apply {
          setAttribute("name", it.name)
          if (!it.translatable) {
            setAttribute("translatable", "false")
          }
          text = it.value
        }
      }
      .toList()
    rootElement.addContent(elements)

    val document = Document(rootElement)
    val xmlOutputter = XMLOutputter(Format.getPrettyFormat())
    Paths.get(outputPath, "$folderName/strings.xml").also { path ->
      path.createParentDirectories()
      xmlOutputter.output(document, path.outputStream())
    }
  }
}

internal fun writePlurals(workbook: Workbook, outputPath: String) {
  val pluralsSheet = workbook.getSheet(PluralsRes.TAG) ?: run {
    outputWarn("Sheet ${PluralsRes.TAG} not found.")
    return
  }
  val pluralsResMap = mutableMapOf<String, MutableList<PluralsRes>>()

  pluralsSheet.rowIterator().asSequence().drop(1).forEachIndexed { rowIndex, row ->
    if (row.isEmpty()) return@forEachIndexed
    row.cellIterator().asSequence().drop(3).forEachIndexed { index, cell ->
      val folderName = pluralsSheet.getRow(0).getCell(index + 3).stringValue
      val quantity = row.getCell(2).stringValue
      if (rowIndex % 6 == 0) {
        val key = row.getCell(0).stringValue
        val translatable = row.getCell(1).stringValue != "false"
        val pluralsRes = PluralsRes(key, translatable).apply {
          values[quantity] = cell.stringValue
        }
        pluralsResMap.getOrPut(folderName) {
          mutableListOf()
        }.add(pluralsRes)
      } else {
        val pluralsRes = pluralsResMap.getValue(folderName)[rowIndex / 6]
        pluralsRes.values[quantity] = cell.stringValue
      }
    }
  }

  pluralsResMap.forEach { (folderName, pluralsResList) ->
    val rootElement = Element("resources")
    pluralsResList.forEach res@{ pluralsRes ->
      val pluralsElement = Element("plurals").apply {
        val elements = pluralsRes.values.asSequence()
          .filter { it.value.isNotEmpty() }
          .map {
            Element("item").apply {
              setAttribute("quantity", it.key)
              text = it.value
            }
          }
          .toList()
        if (elements.isEmpty()) return@res
        setAttribute("name", pluralsRes.name)
        if (!pluralsRes.translatable) {
          setAttribute("translatable", "false")
        }
        addContent(elements)
      }
      rootElement.addContent(pluralsElement)
    }

    val document = Document(rootElement)
    val xmlOutputter = XMLOutputter(Format.getPrettyFormat())
    Paths.get(outputPath, "$folderName/plurals.xml").also { path ->
      path.createParentDirectories()
      xmlOutputter.output(document, path.outputStream())
    }
  }
}

internal fun writeArray(workbook: Workbook, outputPath: String) {
  val arraySheet = workbook.getSheet(ArrayRes.TAG) ?: run {
    outputWarn("Sheet ${ArrayRes.TAG} not found.")
    return
  }
  val arrayResMap = mutableMapOf<String, MutableList<ArrayRes>>()

  arraySheet.rowIterator().asSequence().drop(1).forEach { row ->
    if (row.isEmpty()) return@forEach
    val key = row.getCell(0).stringValue
    val translatable = row.getCell(1).stringValue != "false"
    row.cellIterator().asSequence().drop(2).forEachIndexed { index, cell ->
      val folderName = arraySheet.getRow(0).getCell(index + 2).stringValue
      val value = cell.stringValue
      val arrayList = arrayResMap.getOrPut(folderName) {
        mutableListOf()
      }
      if (key.isEmpty()) {
        arrayList.last().values.mutate() += value
      } else {
        arrayList.add(ArrayRes(key, translatable, mutableListOf(value)))
      }
    }
  }

  arrayResMap.forEach { (folderName, arrayResList) ->
    val rootElement = Element("resources")
    arrayResList.forEach res@{ arrayRes ->
      val arrayElement = Element("string-array").apply {
        val elements = arrayRes.values.asSequence()
          .filter { it.isNotEmpty() }
          .map { Element("item").apply { text = it } }
          .toList()
        if (elements.isEmpty()) return@res
        setAttribute("name", arrayRes.name)
        addContent(elements)
      }
      rootElement.addContent(arrayElement)
    }

    val document = Document(rootElement)
    val xmlOutputter = XMLOutputter(Format.getPrettyFormat())
    Paths.get(outputPath, "$folderName/arrays.xml").also { path ->
      path.createParentDirectories()
      xmlOutputter.output(document, path.outputStream())
    }
  }
}

internal val Cell.stringValue: String
  get() {
    val original = try {
      stringCellValue
    } catch (_: IllegalStateException) {
      outputError("Cell in sheet ${sheet.sheetName} row $rowIndex and column $columnIndex is not a string.")
    }
    return original.trim().replace(NBSP, SPACE)
  }

internal const val NBSP = '\u00A0'
internal const val SPACE = '\u0020'

private fun Row.isEmpty(): Boolean = all { it.stringCellValue.trim().isEmpty() }

private fun <T : Any> List<T>.mutate(): MutableList<T> = this as? MutableList<T>
  ?: outputError("List $this is not mutable.")
