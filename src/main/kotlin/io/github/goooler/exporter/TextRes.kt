package io.github.goooler.exporter

sealed interface TextRes {
  val name: String
  val translatable: Boolean
}

data class StringRes(
  override val name: String,
  override val translatable: Boolean,
  val value: String,
) : TextRes {
  companion object {
    val TAG: String = StringRes::class.java.simpleName

    fun StringRes.map(): StringRes {
      return copy(value = "")
    }
  }
}

data class PluralsRes(
  override val name: String,
  override val translatable: Boolean,
  val values: MutableMap<String, String> = DEFAULT_VALUES.toMutableMap(),
) : TextRes {
  companion object {
    private val DEFAULT_VALUES = mapOf(
      "zero" to "",
      "one" to "",
      "two" to "",
      "few" to "",
      "many" to "",
      "other" to "",
    )
    val TAG: String = PluralsRes::class.java.simpleName

    fun PluralsRes.map(): PluralsRes {
      return copy(values = DEFAULT_VALUES.toMutableMap())
    }
  }
}

data class ArrayRes(
  override val name: String,
  override val translatable: Boolean,
  val values: List<String>,
) : TextRes {
  companion object {
    val TAG: String = ArrayRes::class.java.simpleName

    fun ArrayRes.map(): ArrayRes {
      val emptyValues = List(values.size) { "" }
      return copy(values = emptyValues)
    }
  }
}
