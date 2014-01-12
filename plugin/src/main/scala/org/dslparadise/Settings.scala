package org.dslparadise

object Settings {
  class Setting[T](get: () => T, set: T => Unit) {
    def value = get()
    def value_=(value: T) = set(value)
  }

  def boolSetting(key: String) = new Setting[Boolean](
    get = () => {
      val svalue = System.getProperty("spores." + key)
      svalue != null
    },
    set = value => {
      val svalue = if (value) "true" else null
      System.setProperty("spores." + key, svalue)
    }
  )

  def Ydebug = boolSetting("debug")
}