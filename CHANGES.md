# Release v0.2.1

* Revised treebank-to-dependency conversion to output CoNLL-X format
  * Instead of producing incorrect CoNLL-U files, the conversion process now produces CoNLL-X formatted files.
* Added dependencies required for JDK versions >= 9.0
  * When using JDKs >= 9.0, some dependencies that were previously included must now be added explicitly, e.g. the JAXB dependencies used in this project.
* Removed some unit tests with hard-coded paths that were mistakenly committed previously