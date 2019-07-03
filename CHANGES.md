# Release v0.2.2

* Updated all Document Readers to validate spans as they are imported. Specifically, discontinuous spans are validated in two ways. One, if a discontinuous span contains adjacent component spans, e.g. \[35..43\]\[44..52\], or component spans are are separated by only whitespace, then the component spans are combined, e.g. \[35..52\]. Second, if the discontinuous span contains a component span that is nested in another component span, e.g. \[78..92\]\[88..92\], then the nested span is removed, e.g. \[78..92\].
* Revised the CoNLLCoref Document Writer to exclude two annotation types that are included in the CRAFT coreference annotations, but that should not be included in the CoNLL-Coref 2011/12 file format, namely 'nonreferential pronoun' and 'partonymy relation'.
* Added discontinuous span validation for the CoNLLCorefDocumentWriter. Mapping spans to token boundaries can cause instances of nested discontinuous spans, so the validation code for discontinuous spans was added to the CoNLL-Coref document writer. There was a case in 16628246.xml (coreference annotations) where "7.5 dbc embryos" was annotated as "7" .. "5 dbc embryos". In this case the "7" maps to the "7.5" token and the "5" also maps to the "7.5" token, so the final annotation had two instances of the "7.5" token span. Seems like the original annotation might be faulty, i.e. the "7" .. "5" split, but that's the way it is, so a fix was required.

# Release v0.2.1

* Revised treebank-to-dependency conversion to output CoNLL-X format
  * Instead of producing incorrect CoNLL-U files, the conversion process now produces CoNLL-X formatted files.
* Added dependencies required for JDK versions >= 9.0
  * When using JDKs >= 9.0, some dependencies that were previously included must now be added explicitly, e.g. the JAXB dependencies used in this project.
* Removed some unit tests with hard-coded paths that were mistakenly committed previously