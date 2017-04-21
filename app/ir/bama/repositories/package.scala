package ir.bama

/**
  * @author ahmad
  */
package object repositories {

  object SortOrder extends Enumeration {
    val ASC = Value("ASC")
    val DESC = Value("DESC")
    type SortOrder = Value
  }

}
