package edu.ucdenver.ccp.craft.coreference;

/*
 * #%L
 * Colorado Computational Pharmacology's file conversion
 * 						project
 * %%
 * Copyright (C) 2012 - 2019 Regents of the University of Colorado
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the Regents of the University of Colorado nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import edu.ucdenver.ccp.common.collections.CollectionsUtil;
import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.file.FileUtil;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.file.conversion.conllcoref2012.CoNLLCoref2012DocumentReader;
import edu.ucdenver.ccp.file.conversion.conllcoref2012.CoNLLCoref2012DocumentWriter;
import edu.ucdenver.ccp.file.conversion.conllcoref2012.CoNLLCoref2012DocumentWriter.IncludeCorefType;
import edu.ucdenver.ccp.file.conversion.conllcoref2012.CoNLLCoref2012DocumentWriter.MatchDueTo;
import edu.ucdenver.ccp.file.conversion.knowtator.KnowtatorDocumentReader;
import edu.ucdenver.ccp.file.conversion.knowtator2.Knowtator2DocumentWriter;
import edu.ucdenver.ccp.nlp.core.annotation.Span;
import edu.ucdenver.ccp.nlp.core.annotation.SpanUtils;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotationFactory;
import edu.ucdenver.ccp.nlp.core.mention.ClassMention;
import edu.ucdenver.ccp.nlp.core.mention.ComplexSlotMention;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultClassMention;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultComplexSlotMention;

/**
 * This code was used to update the CRAFT coreference annotations from their original form to the
 * set of annotations released with the CRAFT v3.1 distribution.
 * 
 * The original coreference annotations have been updated in preparation for use in the [CRAFT
 * shared task](https://sites.google.com/view/craft-shared-task-2019/home). The updates described
 * herein were prompted by the desire to use existing scoring software for evaluating coreference
 * system performance. The existing evaluation software requires that there be no overlap among
 * coreference chains, however, the original CRAFT coreference annotations had instances of identity
 * chains that shared mentions. These instances have been resolved in either one of two ways.
 * 
 * 1. All identity chains that shared a mention were manually curated. In cases where the shared
 * mention was found to be an error, the mention was removed from the appropriate identity chain.
 * <br/>
 * 2. In cases where the shared mention was found to be correct for both chains, the identity chains
 * sharing the mention were merged into a single identity chain.
 * 
 * Other changes made during this update include the following: <br/>
 * - identity chains with only a single member, i.e. chains of length one, were removed<br/>
 * - some annotation span offsets were adjusted to remove leading or trailing whitespace<br/>
 * - one instance of an annotation that was only whitespace was removed one annotation's type was
 * changed from _knowtator support class_ to _Noun Phrase_<br/>
 * - some new noun phrase annotations (most were automatically inferred from the spans of the
 * identity chains themselves) were added to complete the identity chain annotations<br/>
 * - in cases where an apposition relation and its head noun phrase were both members of the same
 * identity chain, the noun phrase was removed from the chain and the apposition relation was kept
 * 
 * This code outputs the updated annotations in knowtator-2 format.
 * 
 */
public class CleanCorefAnnotations {

	private static final String PARTONYMY_RELATION = "PARTONYMY relation";
	private static final String KNOWTATOR_SUPPORT_CLASS = "knowtator support class";
	private static final String NONREFERENTIAL_PRONOUN = "Nonreferential pronoun";
	private static final Logger logger = Logger.getLogger(CleanCorefAnnotations.class);

	public static void createKnowtator2Files(File inputDirectory, File txtDirectory, File outputDirectory) throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		String sourceDb = "PMC";

		System.out.println("# Changes to coreference annotations made for CRAFT v3.1\n\n"

				+ "The coreference resolution annotations are described in\n"
				+ "> Cohen, K.B., Lanfranchi, A., Choi, M.J., Bada, M., Baumgartner Jr., W.A., Panteleyeva, N., Verspoor, K., Palmer, M., and Hunter, L.E. (2017) Coreference annotation and resolution in the Colorado Richly Annotated Full Text (CRAFT) corpus of biomedical journal articles. _BMC Bioinformatics_ 18:372. \\[[link](https://bmcbioinformatics.biomedcentral.com/articles/10.1186/s12859-017-1775-9)\\]\n\n"

				+ "As detailed in this document, the original coreference annotations have been updated in preparation for use in the [CRAFT shared task](https://sites.google.com/view/craft-shared-task-2019/home). These udpates have been released as part of CRAFT v3.1. The updates described herein were prompted by the desire to use existing scoring software for evaluating coreference system performance. The existing evaluation software requires that there be no overlap among coreference chains, however, the original CRAFT coreference annotations had instances of identity chains that shared mentions. These instances have been resolved in either one of two ways.\n"

				+ "1. All identity chains that shared a mention were manually curated. In cases where the shared mention was found to be an error, the mention was removed from the appropriate identity chain."
				+ "2. In cases where the shared mention was found to be correct for both chains, the identity chains sharing the mention were merged into a single identity chain."

				+ "Other changes made during this update include the following:"
				+ " * identity chains with only a single member, i.e. chains of length one, were removed"
				+ " * some annotation span offsets were adjusted to remove leading or trailing whitespace"
				+ " * one instance of an annotation that was only whitespace was removed"
				+ " * one annotation's type was changed from _knowtator support class_ to _Noun Phrase_"
				+ " * some new noun phrase annotations (most were automatically inferred from the spans of the identity chains themselves) were added to complete the identity chain annotations"
				+ " * in cases where an apposition relation and its head noun phrase were both members of the same identity chain, the noun phrase was removed from the chain and the apposition relation was kept"

				+ "This updated set of coreference annotations is included in CRAFT starting with the v3.1 distribution using the Knowtator-2 file format, and supplants the original coreference annotations which have been removed from the distribution to avoid confusion. The original coreference annotations, i.e. those that correspond to the original publication, will remain available in the [CRAFT v3.0 distribution](https://github.com/UCDenver-ccp/CRAFT/releases/tag/3.0)\n\n"

				+ "Changes made for each document in the public release of CRAFT v3.1 are detailed below.\n\n");

		/*
		 * Note the log statements in this code were used to populate the README.md file that is now
		 * in the coreference/ directory
		 */

		// File inputDirectory = new File("coreference-annotation/knowtator");
		// File txtDirectory = new File("articles/txt");
		// File outputDirectory = new File("coreference-annotation/knowtator-2");

		Set<String> expectedAnnotationTypes = CollectionsUtil.createSet(CoNLLCoref2012DocumentReader.NOUN_PHRASE,
				CoNLLCoref2012DocumentReader.IDENTITY_CHAIN, CoNLLCoref2012DocumentReader.APPOS_RELATION,
				NONREFERENTIAL_PRONOUN, KNOWTATOR_SUPPORT_CLASS, PARTONYMY_RELATION);
		try {
			/* process each coreference knowtator file individually */
			for (Iterator<File> fileIter = FileUtil.getFileIterator(inputDirectory, false); fileIter.hasNext();) {
				File inputFile = fileIter.next();
				String sourceId = inputFile.getName().replace(".txt.knowtator.xml", "");
				File txtFile = new File(txtDirectory, sourceId + ".txt");
				System.out.println("# " + sourceId + "\n");

				TextDocument td = new KnowtatorDocumentReader().readDocument(sourceId, sourceDb, inputFile, txtFile,
						encoding);

				/* remove leading/trailing whitespace */
				List<TextAnnotation> trimmedAnnotations = trimAnnotations(td.getAnnotations(), td.getText());
				td.setAnnotations(trimmedAnnotations);

				/* create a mapping from annotation type to the annotations of that type */
				Map<String, Set<TextAnnotation>> annotTypeToAnnotsMap = new HashMap<String, Set<TextAnnotation>>();
				for (TextAnnotation ta : td.getAnnotations()) {
					CollectionsUtil.addToOne2ManyUniqueMap(ta.getClassMention().getMentionName(), ta,
							annotTypeToAnnotsMap);
				}

				/* ensure that there aren't any unexpected annotation types */
				Map<String, Integer> typeToCountMap = new HashMap<String, Integer>();
				for (String type : annotTypeToAnnotsMap.keySet()) {
					if (!expectedAnnotationTypes.contains(type)) {
						throw new IllegalStateException("Unexpected annotation type encountered: " + type);
					}
					int size = annotTypeToAnnotsMap.get(type).size();
					typeToCountMap.put(type, size);
				}

				Set<TextAnnotation> npAnnotations = annotTypeToAnnotsMap.get(CoNLLCoref2012DocumentReader.NOUN_PHRASE);
				Set<TextAnnotation> apposAnnotations = annotTypeToAnnotsMap
						.get(CoNLLCoref2012DocumentReader.APPOS_RELATION);
				Set<TextAnnotation> pronounAnnotations = annotTypeToAnnotsMap.get(NONREFERENTIAL_PRONOUN);
				Set<TextAnnotation> partonymyAnnotations = annotTypeToAnnotsMap.get(PARTONYMY_RELATION);

				TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults(sourceId);

				/* clean ident chains step 1: add missing NPs and remove chains of length 1 */
				Set<TextAnnotation> cleanIdentAnnots = new HashSet<TextAnnotation>(
						cleanIdentityChains_step1(td, npAnnotations, factory));

				/* make the curated changes to ident chains */
				makeCuratedIdentityChainModifiations(td.getSourceid(), td.getText(), cleanIdentAnnots, npAnnotations,
						factory);

				/* there is one annotation of type knowtator_support_class that should be a NP */
				if (annotTypeToAnnotsMap.containsKey(KNOWTATOR_SUPPORT_CLASS)) {
					removeKnowtatorSupportClass(annotTypeToAnnotsMap.get(KNOWTATOR_SUPPORT_CLASS), npAnnotations);
				}

				/* clean ident chains step 2: merge chains with shared mentions */
				cleanIdentAnnots = cleanIdentityChains_step2(cleanIdentAnnots, npAnnotations, td.getText(), factory);

				/* clean appos relations */
				List<TextAnnotation> cleanApposAnnots = cleanApposRelations(apposAnnotations, npAnnotations,
						td.getText(), factory);

				/* compile all cleaned annotations */
				List<TextAnnotation> cleanAnnots = new ArrayList<TextAnnotation>();
				cleanAnnots.addAll(cleanIdentAnnots);
				cleanAnnots.addAll(cleanApposAnnots);
				cleanAnnots.addAll(npAnnotations);
				if (pronounAnnotations != null) {
					cleanAnnots.addAll(pronounAnnotations);
				}
				if (partonymyAnnotations != null) {
					cleanAnnots.addAll(partonymyAnnotations);
				}

				System.out.println("## Summary for " + td.getSourceid());
				System.out.println(" * IDENT chain annotations delta: "
						+ (cleanIdentAnnots.size() - typeToCountMap.get("IDENTITY chain")));
				System.out.println(" * Noun phrase annotations delta: "
						+ (npAnnotations.size() - typeToCountMap.get("Noun Phrase")));
				System.out.println(" * APPOS relation annotations delta: "
						+ (cleanApposAnnots.size() - typeToCountMap.get("APPOS relation")));
				System.out.println(" * Non-referential pronoun annotations delta: " + ((pronounAnnotations == null) ? 0
						: +(pronounAnnotations.size() - typeToCountMap.get(NONREFERENTIAL_PRONOUN))));
				System.out.println(" * Partonymy relation annotations delta: " + ((partonymyAnnotations == null) ? 0
						: +(partonymyAnnotations.size() - typeToCountMap.get(PARTONYMY_RELATION))));

				/* output the clean annotations to knowtator-2 format */
				td.setAnnotations(cleanAnnots);
				File outputFile = new File(outputDirectory, td.getSourceid() + ".xml");
				new Knowtator2DocumentWriter().serialize(td, outputFile, encoding);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * This method organizes the curated changes for each document
	 * 
	 * @param sourceId
	 * @param documentText
	 * @param identAnnotations
	 * @param npAnnotations
	 * @param factory
	 */
	private static void makeCuratedIdentityChainModifiations(String sourceId, String documentText,
			Set<TextAnnotation> identAnnotations, Set<TextAnnotation> npAnnotations, TextAnnotationFactory factory) {

		Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap = getMemberToChainAnnotMap(identAnnotations);

		switch (sourceId) {
		case "11532192":
			curatedChangesFor_11532192(identAnnotations, memberToChainAnnotMap);
			break;
		case "11597317":
			curatedChangesFor_11597317(identAnnotations, memberToChainAnnotMap);
			break;
		case "11897010":
			curatedChangesFor_11897010(identAnnotations, memberToChainAnnotMap);
			break;
		case "12079497":
			curatedChangesFor_12079497(identAnnotations, memberToChainAnnotMap);
			break;
		case "12546709":
			curatedChangesFor_12546709(identAnnotations, memberToChainAnnotMap);
			break;
		case "12585968":
			curatedChangesFor_12585968(identAnnotations, memberToChainAnnotMap);
			break;
		case "12925238":
			curatedChangesFor_12925238(identAnnotations, memberToChainAnnotMap);
			break;
		case "14609438":
			curatedChangesFor_14609438(identAnnotations, memberToChainAnnotMap);
			break;
		case "14611657":
			curatedChangesFor_14611657(identAnnotations, memberToChainAnnotMap);
			break;
		case "14723793":
			curatedChangesFor_14723793(identAnnotations, memberToChainAnnotMap);
			break;
		case "14737183":
			curatedChangesFor_14737183(identAnnotations, memberToChainAnnotMap);
			break;
		case "15005800":
			curatedChangesFor_15005800(identAnnotations, memberToChainAnnotMap, npAnnotations, documentText, factory);
			break;
		case "15040800":
			curatedChangesFor_15040800(identAnnotations, memberToChainAnnotMap);
			break;
		case "15061865":
			curatedChangesFor_15061865(identAnnotations, memberToChainAnnotMap, npAnnotations, documentText, factory);
			break;
		case "15207008":
			curatedChangesFor_15207008(identAnnotations, memberToChainAnnotMap);
			break;
		case "15314655":
			curatedChangesFor_15314655(identAnnotations, memberToChainAnnotMap);
			break;
		case "15314659":
			curatedChangesFor_15314659(identAnnotations, memberToChainAnnotMap);
			break;
		case "15320950":
			curatedChangesFor_15320950(identAnnotations, memberToChainAnnotMap, npAnnotations, documentText, factory);
			break;
		case "15328533":
			curatedChangesFor_15328533(identAnnotations, memberToChainAnnotMap);
			break;
		case "15345036":
			curatedChangesFor_15345036(identAnnotations, memberToChainAnnotMap);
			break;
		case "15492776":
			curatedChangesFor_15492776(identAnnotations, memberToChainAnnotMap);
			break;
		case "15550985":
			curatedChangesFor_15550985(identAnnotations, memberToChainAnnotMap);
			break;
		case "15588329":
			curatedChangesFor_15588329(identAnnotations, memberToChainAnnotMap);
			break;
		case "15630473":
			curatedChangesFor_15630473(identAnnotations, memberToChainAnnotMap);
			break;
		case "15676071":
			curatedChangesFor_15676071(identAnnotations, memberToChainAnnotMap);
			break;
		case "15760270":
			curatedChangesFor_15760270(identAnnotations, memberToChainAnnotMap, npAnnotations, documentText, factory);
			break;
		case "15819996":
			curatedChangesFor_15819996(identAnnotations, memberToChainAnnotMap, npAnnotations, documentText, factory);
			break;
		case "15836427":
			curatedChangesFor_15836427(identAnnotations, memberToChainAnnotMap);
			break;
		case "15876356":
			curatedChangesFor_15876356(identAnnotations, memberToChainAnnotMap);
			break;
		case "15917436":
			curatedChangesFor_15917436(identAnnotations, memberToChainAnnotMap);
			break;
		case "15921521":
			curatedChangesFor_15921521(identAnnotations, memberToChainAnnotMap);
			break;
		case "15938754":
			curatedChangesFor_15938754(identAnnotations, memberToChainAnnotMap);
			break;
		case "16098226":
			curatedChangesFor_16098226(identAnnotations, memberToChainAnnotMap);
			break;
		case "16103912":
			curatedChangesFor_16103912(identAnnotations, memberToChainAnnotMap, npAnnotations, documentText, factory);
			break;
		case "16109169":
			curatedChangesFor_16109169(identAnnotations, memberToChainAnnotMap);
			break;
		case "16110338":
			curatedChangesFor_16110338(identAnnotations, memberToChainAnnotMap);
			break;
		case "16121255":
			curatedChangesFor_16121255(identAnnotations, memberToChainAnnotMap, npAnnotations, documentText, factory);
			break;
		case "16121256":
			curatedChangesFor_16121256(identAnnotations, memberToChainAnnotMap, npAnnotations, documentText, factory);
			break;
		case "16216087":
			curatedChangesFor_16216087(identAnnotations, memberToChainAnnotMap, npAnnotations, documentText, factory);
			break;
		case "16221973":
			curatedChangesFor_16221973(identAnnotations, memberToChainAnnotMap, npAnnotations, documentText, factory);
			break;
		case "16255782":
			curatedChangesFor_16255782(identAnnotations, memberToChainAnnotMap, npAnnotations, documentText, factory);
			break;
		case "16279840":
			curatedChangesFor_16279840(identAnnotations, memberToChainAnnotMap);
			break;
		case "16362077":
			curatedChangesFor_16362077(identAnnotations, memberToChainAnnotMap);
			break;
		case "16433929":
			curatedChangesFor_16433929(identAnnotations, memberToChainAnnotMap);
			break;
		case "16462940":
			curatedChangesFor_16462940(identAnnotations, memberToChainAnnotMap);
			break;
		case "16504143":
			curatedChangesFor_16504143(identAnnotations, memberToChainAnnotMap);
			break;
		case "16504174":
			curatedChangesFor_16504174(identAnnotations, memberToChainAnnotMap);
			break;
		case "16507151":
			curatedChangesFor_16507151(identAnnotations, memberToChainAnnotMap);
			break;
		case "16539743":
			curatedChangesFor_16539743(identAnnotations, memberToChainAnnotMap);
			break;
		case "16579849":
			curatedChangesFor_16579849(identAnnotations, memberToChainAnnotMap);
			break;
		case "16628246":
			curatedChangesFor_16628246(identAnnotations, memberToChainAnnotMap, npAnnotations, documentText, factory);
			break;
		case "16670015":
			curatedChangesFor_16670015(identAnnotations, memberToChainAnnotMap);
			break;
		case "16700629":
			curatedChangesFor_16700629(identAnnotations, memberToChainAnnotMap, npAnnotations, documentText, factory);
			break;
		case "16870721":
			curatedChangesFor_16870721(identAnnotations, memberToChainAnnotMap);
			break;
		case "17002498":
			curatedChangesFor_17002498(identAnnotations, memberToChainAnnotMap);
			break;
		case "17020410":
			curatedChangesFor_17020410(identAnnotations, memberToChainAnnotMap);
			break;
		case "17022820":
			curatedChangesFor_17022820(identAnnotations, memberToChainAnnotMap);
			break;
		case "17069463":
			curatedChangesFor_17069463(identAnnotations, memberToChainAnnotMap, npAnnotations, documentText, factory);
			break;
		case "17078885":
			curatedChangesFor_17078885(identAnnotations, memberToChainAnnotMap);
			break;
		case "17083276":
			curatedChangesFor_17083276(identAnnotations, memberToChainAnnotMap);
			break;
		case "17194222":
			curatedChangesFor_17194222(identAnnotations, memberToChainAnnotMap);
			break;
		case "17244351":
			curatedChangesFor_17244351(identAnnotations, memberToChainAnnotMap);
			break;
		case "17425782":
			curatedChangesFor_17425782(identAnnotations, memberToChainAnnotMap);
			break;
		case "17447844":
			curatedChangesFor_17447844(identAnnotations, memberToChainAnnotMap);
			break;
		case "17590087":
			curatedChangesFor_17590087(identAnnotations, memberToChainAnnotMap);
			break;
		case "17608565":
			curatedChangesFor_17608565(identAnnotations, memberToChainAnnotMap);
			break;
		case "17696610":
			curatedChangesFor_17696610(identAnnotations, memberToChainAnnotMap);
			break;
		default:
			throw new IllegalArgumentException("curated changes for document " + sourceId + " are not available.");
		}

	}

	/**
	 * @param identAnnotations
	 * @return mapping from annotation that is a member of a chain to the set of IDENT annotations
	 *         to which it belongs
	 */
	private static Map<TextAnnotation, Set<TextAnnotation>> getMemberToChainAnnotMap(
			Set<TextAnnotation> identAnnotations) {
		Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap = new HashMap<TextAnnotation, Set<TextAnnotation>>();
		for (TextAnnotation identAnnot : identAnnotations) {
			ComplexSlotMention csm = identAnnot.getClassMention()
					.getComplexSlotMentionByName(CoNLLCoref2012DocumentReader.IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT);
			for (ClassMention cm : csm.getClassMentions()) {
				CollectionsUtil.addToOne2ManyUniqueMap(cm.getTextAnnotation(), identAnnot, memberToChainAnnotMap);
			}
		}
		return memberToChainAnnotMap;
	}

	private static void curatedChangesFor_11532192(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(4093, 4107)),
				CollectionsUtil.createList(new Span(38589, 38596), new Span(38609, 38613)), identAnnotations,
				memberToChainAnnotMap,
				"removing from the chain that links mentions of strains, keeping in the chain that links mentions of mice");

		removeAnnotationFromChain(180, 184, 13156, 13165, identAnnotations, memberToChainAnnotMap,
				"this is a reference to a specific mouse used in an experiment. Removing from the longer chain that appears to be about mice in general, and keeping in the other (shorter) chain that appears to be about the specific mice used in the experiments.");
	}

	private static void curatedChangesFor_11597317(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(289, 313, 3869, 3876, identAnnotations, memberToChainAnnotMap,
				"the longer chain appears to be about DSBs in general, whereas the other (shorter) chain appears to be about a specific DSB.");

	}

	private static void curatedChangesFor_11897010(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(6966, 7008, 8170, 8207, identAnnotations, memberToChainAnnotMap,
				"removing from a chain that appears to be about the band not the variant");

	}

	private static void curatedChangesFor_12079497(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {
		// no changes
	}

	private static void curatedChangesFor_12546709(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(5847, 5859, 22723, 22731, identAnnotations, memberToChainAnnotMap,
				"removing from a chain that is about lenses (plural) and keeping in the chain that is about lens (singular)");

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(1231, 1235), new Span(1277, 1294)),
				CollectionsUtil.createList(new Span(26445, 26457)), identAnnotations, memberToChainAnnotMap,
				"'these lenses' refers to 'alphaA/BKO lenses' including 5 wk old, but also older");

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(1231, 1235), new Span(1277, 1294)),
				CollectionsUtil.createList(new Span(26651, 26663)), identAnnotations, memberToChainAnnotMap,
				"'these lenses' refers to 'alphaA/BKO lenses' including 5 wk old, but also older");

		removeAnnotationFromChain(569, 579, 33799, 33809, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'paper' from chain about 'this study'");

		removeAnnotationFromChain(1157, 1169, 31847, 31940, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'nucleic acid staining' from chain about 'nucleic acid'");

		removeAnnotationFromChain(8320, 8348, 8560, 8646, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'sections' from chain about 'statistical analysis'");

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(1231, 1235), new Span(1277, 1294)),
				CollectionsUtil.createList(new Span(11067, 11092)), identAnnotations, memberToChainAnnotMap,
				"remove reference to 'wild type lenses' from chain about 'alphaA/BKO lenses'");

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(1231, 1235), new Span(1277, 1294)),
				CollectionsUtil.createList(new Span(11099, 11128)), identAnnotations, memberToChainAnnotMap,
				"remove reference to 'wild type lenses' from chain about 'alphaA/BKO lenses'");

	}

	private static void curatedChangesFor_12585968(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(7202, 7259, 10292, 10309, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'canal cristae' from chain about 'crista innervation'");

		removeAnnotationFromChain(16548, 16575, 30766, 30785, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'cochlear nuclei' from chain about 'cochlear nerve'");

	}

	private static void curatedChangesFor_12925238(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(1353, 1378, 25359, 25375, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'cytoskeleton' from chain about 'membrane cytoskeleton'");

	}

	private static void curatedChangesFor_14609438(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {
		// no chains were merged for this document
	}

	private static void curatedChangesFor_14611657(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(628, 651, 18476, 18500, identAnnotations, memberToChainAnnotMap,
				"keeping reference to 'olfactory receptor genes' in the chain about 'six olfactory receptor genes' due to context within the document");

	}

	private static void curatedChangesFor_14723793(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(10934, 10949, 13289, 13350, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'analysis' from a chain about 'results'");

		removeAnnotationFromChain(11847, 11907, 12560, 12562, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'antibody' (it) from a chain about the 'specificity of the antibody'");

		removeAnnotationFromChain(873, 886, 2449, 2456, identAnnotations, memberToChainAnnotMap,
				"remove reference to general 'protein' from a chain about 'Acdp proteins'");

		removeAnnotationFromChain(3720, 3766, 3869, 3878, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'study' from a chain about 'Nothern blots'");

		removeAnnotationFromChain(635, 675, 679, 684, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'their' from a chain about 'sequences' as it refers to the genes");

	}

	private static void curatedChangesFor_14737183(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(16135, 16142, 63828, 63839, identAnnotations, memberToChainAnnotMap,
				"keep reference to 'the ventrum' in chain about 'ventrum in at/at mice' based on context in document");

		removeAnnotationFromChain(5980, 5992, 25129, 25141, identAnnotations, memberToChainAnnotMap,
				"keep reference to 'ventral skin' in chain about 'embryonic ventral skin' based on context in document");

		removeAnnotationFromChain(6249, 6287, 6249, 6287, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'transcript' from chain about 'expression'");

	}

	private static void curatedChangesFor_15005800(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap, Set<TextAnnotation> npAnnotations,
			String documentText, TextAnnotationFactory factory) {
		removeAnnotationFromChain(36, 83, 203, 275, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'mice' from chain about 'models'");

		addAnnotationToChain(36, 83, 277, 336, identAnnotations, memberToChainAnnotMap, npAnnotations, documentText,
				factory, "replaced appos head with appos attribute in the chain about models");

		removeAnnotationFromChain(3761, 3814, 18010, 18020, identAnnotations, memberToChainAnnotMap,
				"'the latter' refers to '8-DHC' not '7-DHC'");

		removeAnnotationFromChain(7278, 7313, 7278, 7313, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'delayed type I AECs differentiation' from the chain that's not about 'delayed' differentiation");

		removeAnnotationFromChain(737, 745, 8567, 8575, identAnnotations, memberToChainAnnotMap,
				"this instance of 'Dhcr7-/-' is a reference to embryos, so keep in the chain about embryos");

		removeAnnotationFromChain(1716, 1725, 37079, 37088, identAnnotations, memberToChainAnnotMap,
				"this instance of 'wild-type' is a reference to embryos, so keep in the chain about embryos");

	}

	private static void curatedChangesFor_15040800(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		// no chains were merged in this document

	}

	private static void curatedChangesFor_15061865(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap, Set<TextAnnotation> npAnnotations,
			String documentText, TextAnnotationFactory factory) {

		removeAnnotationFromChain(10103, 10120, 10216, 10224, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'ommission' from chain about 'errors'");

		removeAnnotationFromChain(10103, 10120, 27822, 27830, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'ommission' from chain about 'errors'");

		addAnnotationToChain(10112, 10120, 27822, 27830, identAnnotations, memberToChainAnnotMap, npAnnotations,
				documentText, factory,
				"move 'ommission' annotation from one chain to another for consistency purposes");

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(870, 879)),
				CollectionsUtil.createList(new Span(29249, 29258), new Span(29268, 29272)), identAnnotations,
				memberToChainAnnotMap, "remove reference of 'wild-type mice' from chain about 'wild-type' ");

	}

	private static void curatedChangesFor_15207008(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(886, 897, 18996, 19001, identAnnotations, memberToChainAnnotMap,
				"remove reference to generic 'TACC3' from chain about 'human TACC3'");

		removeAnnotationFromChain(23719, 23747, 34792, 34801, identAnnotations, memberToChainAnnotMap,
				"remove reference to generic 'the TACCs' from chain about 'vertebrate TACCs'");

		removeAnnotationFromChain(9207, 9212, 36501, 36506, identAnnotations, memberToChainAnnotMap,
				"remove protein reference of 'TACC2' from chain of gene references for 'TACC2'");

		removeAnnotationFromChain(29884, 29889, 41003, 41008, identAnnotations, memberToChainAnnotMap,
				"remove 'their' from chain as it references 'exons' in text, not TACC1");

		removeAnnotationFromChain(3103, 3149, 29884, 29889, identAnnotations, memberToChainAnnotMap,
				"remove 'TACC1' protein reference from chain of gene references");

		removeAnnotationFromChain(7910, 7918, 8436, 8444, identAnnotations, memberToChainAnnotMap,
				"remove 'trTACC1B' reference from chain about 'trTACC1A'");

		removeAnnotationFromChain(36, 67, 41836, 41840, identAnnotations, memberToChainAnnotMap,
				"remove 'TACC' gene reference from chain of protein references");

		removeAnnotationFromChain(29914, 29929, 35170, 35172, identAnnotations, memberToChainAnnotMap,
				"'it' references 'TACC3' not 'Aurora Kinase A'");

		removeAnnotationFromChain(25529, 25550, 32353, 32369, identAnnotations, memberToChainAnnotMap,
				"remove 'vertebrate TACCs' protein reference from chain about 'vertebrate TACCs genes'");

	}

	private static void curatedChangesFor_15314655(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(0, 28, 36627, 36632, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'tif1γ' from chain about 'mon' ");
		removeAnnotationFromChain(0, 28, 36634, 36639, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'tif1γ' from chain about 'mon' ");
		removeAnnotationFromChain(0, 28, 36707, 36712, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'tif1γ' from chain about 'mon' ");
		removeAnnotationFromChain(0, 28, 36846, 36851, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'tif1γ' from chain about 'mon' ");

		removeAnnotationFromChain(951, 973, 7263, 7274, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'mon mutants' from chain about 'homozygous mon mutants' ");

		removeAnnotationFromChain(951, 973, 7373, 7384, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'mon mutants' from chain about 'homozygous mon mutants' ");

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(5017, 5027), new Span(5038, 5049)),
				CollectionsUtil.createList(new Span(37547, 37552)), identAnnotations, memberToChainAnnotMap,
				"remove reference of 'Tif1γ' protein from chain about 'Tif1γ genes' ");

		removeAnnotationFromChain(6950, 6953, 33451, 33469, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'scl' from chain about 'scl transcription factor' ");

	}

	private static void curatedChangesFor_15314659(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(34164, 34180, 34754, 34770, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'marker locations' from chain about 'interval mapping' ");

		removeAnnotationFromChain(12352, 12374, 27836, 27843, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'C57BL/6' from chain about 'C57BL/6 background' ");

		removeAnnotationFromChain(8162, 8198, 29656, 29666, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'anti-dsDNA' from chain about 'anti-dsDNA Abs' ");

		removeAnnotationFromChain(34164, 34180, 37145, 37171, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'highly significant linkage' from chain about 'interval mapping' ");

	}

	private static void curatedChangesFor_15320950(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap, Set<TextAnnotation> npAnnotations,
			String documentText, TextAnnotationFactory factory) {

		removeAnnotationFromChain(375, 402, 25985, 26000, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'Slc26a4-/- mice' from chain about 'Slc26a4+/+ mice' ");

		removeAnnotationFromChain(691, 722, 1253, 1386, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'endocochlear potential' from chain about 'double-barreled microelectrodes' ");

		removeAnnotationFromChain(746, 770, 23723, 23739, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'stria vascularis' from chain about 'stria marginal cells' ");

		removeAnnotationFromChain(375, 402, 27820, 27830, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'Slc26a4+/+' from chain about 'Slc26a4+/+ mice' ");

		addAnnotationToChain(375, 402, 27820, 27835, identAnnotations, memberToChainAnnotMap, npAnnotations,
				documentText, factory, "remove reference of 'Slc26a4+/+' from chain about 'Slc26a4+/+ mice' ");

		removeAnnotationFromChain(375, 402, 13316, 13331, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'Slc26a4-/- mice' from chain about 'Slc26a4+/+ mice' ");

	}

	private static void curatedChangesFor_15328533(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {
		// no chains merge for this document
	}

	private static void curatedChangesFor_15345036(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(1155, 1174, 67718, 67739, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'wild-type littermates' from chain about 'mutant littermates' ");

		removeAnnotationFromChain(7339, 7362, 65673, 65694, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'littermates' from chain about 'embryos' ");

	}

	private static void curatedChangesFor_15492776(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(41165, 41198, 51268, 51279, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'Gail Martin' from chain about 'nuclear-localized Cre recombinase' ");

		removeAnnotationFromChain(16354, 16370, 26511, 26534, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'articular cartilage' from chain about 'ankle region' ");

		removeAnnotationFromChain(9340, 9353, 46109, 46117, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'Gdf5-Cre' from chain about 'Gdf5-Cre transgene' ");

		removeAnnotationFromChain(25734, 25755, 25734, 25755, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'articular surface' from chain about 'inhibition' ");

		removeAnnotationFromChain(53349, 53365, 61679, 61695, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'white arrow heads' from chain about 'black arrow heads' ");

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(3031, 3052)),
				CollectionsUtil.createList(new Span(21084, 21092), new Span(21106, 21119)), identAnnotations,
				memberToChainAnnotMap, "remove reference of 'cellular proliferation' from chain about 'cell death' ");

		removeAnnotationFromChain(358, 363, 18647, 18659, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'ankle joints' from chain about 'joint' ");

	}

	private static void curatedChangesFor_15550985(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {
		removeAnnotationFromChain(186, 204, 15677, 15708, identAnnotations, memberToChainAnnotMap,
				"the chain links mentions of mice, not 'normal motor neuron innervation'");

	}

	private static void curatedChangesFor_15588329(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {
		// no manual changes needed
	}

	private static void curatedChangesFor_15630473(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {
		// no manual changes needed
	}

	private static void curatedChangesFor_15676071(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(15331, 15340, 16108, 16117, identAnnotations, memberToChainAnnotMap,
				"remove references to 'Figure 7B' from chain of references for 'Figure 7A'");

		removeAnnotationFromChain(15331, 15340, 17018, 17019, identAnnotations, memberToChainAnnotMap,
				"remove references to 'Figure 7B' from chain of references for 'Figure 7A'");

		removeAnnotationFromChain(6881, 6902, 7954, 7960, identAnnotations, memberToChainAnnotMap,
				"annotation 'Crx+/+' references retinas not photoreceptors");

	}

	private static void curatedChangesFor_15760270(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap, Set<TextAnnotation> npAnnotations,
			String documentText, TextAnnotationFactory factory) {
		removeAnnotationFromChain(10332, 10341, 67642, 67643, identAnnotations, memberToChainAnnotMap,
				"remove references to 'Figure 1B' from chain of references for 'Figure 2B'");

		removeAnnotationFromChain(20409, 20413, 74425, 74429, identAnnotations, memberToChainAnnotMap,
				"remove references to appos 'cold' from chain of references for '4 °C'; will replace with appos attribute annotation for '4 °C'");

		addAnnotationToChain(20409, 20413, 74431, 74435, identAnnotations, memberToChainAnnotMap, npAnnotations,
				documentText, factory,
				"replacing appos head with appos attribute annotation in chain of references for '4 °C'");

		removeAnnotationFromChain(20409, 20413, 74771, 74775, identAnnotations, memberToChainAnnotMap,
				"remove references to appos 'cold' from chain of references for '4 °C'; will replace with appos attribute annotation for '4 °C'");

		addAnnotationToChain(20409, 20413, 74777, 74781, identAnnotations, memberToChainAnnotMap, npAnnotations,
				documentText, factory,
				"replacing appos head with appos attribute annotation in chain of references for '4 °C'");

		removeAnnotationFromChain(1409, 1427, 28928, 28937, identAnnotations, memberToChainAnnotMap,
				"remove reference to genotype 'PGC-1α+/+' from chain about control animals");

	}

	private static void curatedChangesFor_15819996(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap, Set<TextAnnotation> npAnnotations,
			String documentText, TextAnnotationFactory factory) {

		removeAnnotationFromChain(24, 34, 13178, 13185, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'neurons' from chain about 'Annexin A7'");

		removeAnnotationFromChain(1821, 1837, 25205, 25210, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'mouse' from chain about 'adult mice'");

		/*
		 * there are two ident chains that start with the 'antibody staining' annotation. We don't
		 * know which will be processed first, so we remove from both of them, then add back to the
		 * proper one.
		 */
		removeAnnotationFromChain(19549, 19566, 19549, 19566, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'antibody staining' from chain about 'antibodies'; there are two identity chains that start with 'antibody staining'. One is correct, the other is not. However, we do not know which identity chain will be processed first, so we remove from both and then add back to the proper chain afterwards.");

		removeAnnotationFromChain(19549, 19566, 19549, 19566, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'antibody staining' from chain about 'antibodies'; there are two identity chains that start with 'antibody staining'. One is correct, the other is not. However, we do not know which identity chain will be processed first, so we remove from both and then add back to the proper chain afterwards.");

		addAnnotationToChain(34564, 34581, 19549, 19566, identAnnotations, memberToChainAnnotMap, npAnnotations,
				documentText, factory,
				"adding 'antibody staining' back to proper chain. Had to remove from both b/c there were 2 identity chains that started with the annotation to-be-removed.");

	}

	private static void curatedChangesFor_15836427(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(3153, 3196, 42907, 42911, identAnnotations, memberToChainAnnotMap,
				"remove reference to singular 'Er81' from chain about multiple proteins");

	}

	private static void curatedChangesFor_15876356(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {
		// no manual changes
	}

	private static void curatedChangesFor_15917436(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {
		// no manual changes
	}

	private static void curatedChangesFor_15921521(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(4940, 4965, 5588, 5619, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'mice' from chain about 'background'");

		removeAnnotationFromChain(4940, 4965, 15372, 15397, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'mice' from chain about 'background'");

	}

	private static void curatedChangesFor_15938754(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(1048, 1056), new Span(1077, 1087)),
				CollectionsUtil.createList(new Span(8579, 8595)), identAnnotations, memberToChainAnnotMap,
				"'these RI strains' refers to the '17', not the 'two'");

	}

	private static void curatedChangesFor_16098226(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(26668, 26735, 26769, 26790, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'the PAX6PST construct' from chain about 'PAX6 PST domain construct containing the mutation 1627A>G (Q422R)' as they are not the same");

	}

	private static void curatedChangesFor_16103912(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap, Set<TextAnnotation> npAnnotations,
			String documentText, TextAnnotationFactory factory) {

		removeAnnotationFromChain(8087, 8102, 22325, 22329, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'Fog2' from chain about 'mice'");
		removeAnnotationFromChain(8387, 8402, 22325, 22329, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'Fog2' from chain about 'mice'");

		addAnnotationToChain(8087, 8102, 22321, 22336, identAnnotations, memberToChainAnnotMap, npAnnotations,
				documentText, factory, "adding 'the Fog2 mutant' to chain where 'Fog2' was just removed");

		removeAnnotationFromChain(13935, 13950, 24229, 24244, identAnnotations, memberToChainAnnotMap,
				"removing expression about mutant lungs from chain about expression in non-mutant lungs");

		removeAnnotationFromChain(42, 46, 2277, 2286, identAnnotations, memberToChainAnnotMap,
				"removing reference to small lungs from chain about lungs in general");

	}

	private static void curatedChangesFor_16109169(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {
		removeAnnotationFromChain(522, 570, 9328, 9345, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'wild-type embryos' from chain about 'mutant embryos'");
	}

	private static void curatedChangesFor_16110338(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(26202, 26214, 26202, 26214, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'type I derepression' from chain about 'type I cone genes'");

		removeAnnotationFromChain(56963, 57002, 57374, 57415, identAnnotations, memberToChainAnnotMap,
				"removing reference to down-regulated genes from chain about up-regulated genes");

		removeAnnotationFromChain(18509, 18526, 56632, 56664, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'all three microarray experiments' from chain about 'three experiments'");

		removeAnnotationFromChain(34, 51, 38559, 38576, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'rod and cone genes' from chain about 'photoreceptors'");

		removeAnnotationFromChain(735, 738, 53427, 53437, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'rd7 mutant' from chain about 'rd7'");

		removeAnnotationFromChain(63, 68, 5030, 5039, identAnnotations, memberToChainAnnotMap,
				"removing reference to mutant 'mouse' from chain about mice in general");
	}

	private static void curatedChangesFor_16121255(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap, Set<TextAnnotation> npAnnotations,
			String documentText, TextAnnotationFactory factory) {

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8034, 8039), new Span(8056, 8077)),
				CollectionsUtil.createList(new Span(14554, 14568)), identAnnotations, memberToChainAnnotMap,
				"removing reference to protein from chain about mice");
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8034, 8039), new Span(8056, 8077)),
				CollectionsUtil.createList(new Span(14932, 14946)), identAnnotations, memberToChainAnnotMap,
				"removing reference to protein from chain about mice");
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8034, 8039), new Span(8056, 8077)),
				CollectionsUtil.createList(new Span(16968, 16982)), identAnnotations, memberToChainAnnotMap,
				"removing reference to protein from chain about mice");
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8034, 8039), new Span(8056, 8077)),
				CollectionsUtil.createList(new Span(17600, 17614)), identAnnotations, memberToChainAnnotMap,
				"removing reference to protein from chain about mice");
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8034, 8039), new Span(8056, 8077)),
				CollectionsUtil.createList(new Span(19862, 19876)), identAnnotations, memberToChainAnnotMap,
				"removing reference to protein from chain about mice");
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8034, 8039), new Span(8056, 8077)),
				CollectionsUtil.createList(new Span(20068, 20082)), identAnnotations, memberToChainAnnotMap,
				"removing reference to protein from chain about mice");
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8034, 8039), new Span(8056, 8077)),
				CollectionsUtil.createList(new Span(24620, 24634)), identAnnotations, memberToChainAnnotMap,
				"removing reference to protein from chain about mice");
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8034, 8039), new Span(8056, 8077)),
				CollectionsUtil.createList(new Span(24880, 24894)), identAnnotations, memberToChainAnnotMap,
				"removing reference to protein from chain about mice");
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8034, 8039), new Span(8056, 8077)),
				CollectionsUtil.createList(new Span(27865, 27879)), identAnnotations, memberToChainAnnotMap,
				"removing reference to protein from chain about mice");
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8034, 8039), new Span(8056, 8077)),
				CollectionsUtil.createList(new Span(30600, 30614)), identAnnotations, memberToChainAnnotMap,
				"removing reference to protein from chain about mice");
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8034, 8039), new Span(8056, 8077)),
				CollectionsUtil.createList(new Span(31625, 31639)), identAnnotations, memberToChainAnnotMap,
				"removing reference to protein from chain about mice");
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8034, 8039), new Span(8056, 8077)),
				CollectionsUtil.createList(new Span(33436, 33450)), identAnnotations, memberToChainAnnotMap,
				"removing reference to protein from chain about mice");
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8034, 8039), new Span(8056, 8077)),
				CollectionsUtil.createList(new Span(40438, 40452)), identAnnotations, memberToChainAnnotMap,
				"removing reference to protein from chain about mice");
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8034, 8039), new Span(8056, 8077)),
				CollectionsUtil.createList(new Span(40503, 40517)), identAnnotations, memberToChainAnnotMap,
				"removing reference to protein from chain about mice");
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8034, 8039), new Span(8056, 8077)),
				CollectionsUtil.createList(new Span(40818, 40832)), identAnnotations, memberToChainAnnotMap,
				"removing reference to protein from chain about mice");
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8034, 8039), new Span(8056, 8077)),
				CollectionsUtil.createList(new Span(40903, 40917)), identAnnotations, memberToChainAnnotMap,
				"removing reference to protein from chain about mice");
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8034, 8039), new Span(8056, 8077)),
				CollectionsUtil.createList(new Span(41328, 41342)), identAnnotations, memberToChainAnnotMap,
				"removing reference to protein from chain about mice");
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8034, 8039), new Span(8056, 8077)),
				CollectionsUtil.createList(new Span(41417, 41431)), identAnnotations, memberToChainAnnotMap,
				"removing reference to protein from chain about mice");
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8034, 8039), new Span(8056, 8077)),
				CollectionsUtil.createList(new Span(41578, 41592)), identAnnotations, memberToChainAnnotMap,
				"removing reference to protein from chain about mice");

		addAnnotationToChain(1322, 1339, 17600, 17614, identAnnotations, memberToChainAnnotMap, npAnnotations,
				documentText, factory, "adding reference to protein that was just removed from a chain about mice");

		removeAnnotationFromChain(8034, 8077, 11999, 12019, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'heterozygous' from chain about 'heterozygous or wild-type'");
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8034, 8077)),
				CollectionsUtil.createList(new Span(12293, 12305), new Span(12318, 12325)), identAnnotations,
				memberToChainAnnotMap,
				"removing reference to 'heterozygous' from chain about 'heterozygous or wild-type'");
		removeAnnotationFromChain(8034, 8077, 15861, 15908, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'heterozygous' from chain about 'heterozygous or wild-type'");
		removeAnnotationFromChain(8034, 8077, 15930, 15935, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'heterozygous' from chain about 'heterozygous or wild-type'");
		removeAnnotationFromChain(8034, 8077, 16192, 16212, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'heterozygous' from chain about 'heterozygous or wild-type'");
		removeAnnotationFromChain(8034, 8077, 23784, 23801, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'heterozygous' from chain about 'heterozygous or wild-type'");
		removeAnnotationFromChain(8034, 8077, 23849, 23866, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'heterozygous' from chain about 'heterozygous or wild-type'");
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8034, 8077)),
				CollectionsUtil.createList(new Span(36650, 36662), new Span(36678, 36682)), identAnnotations,
				memberToChainAnnotMap,
				"removing reference to 'heterozygous' from chain about 'heterozygous or wild-type'");
		removeAnnotationFromChain(8034, 8077, 36877, 36889, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'heterozygous' from chain about 'heterozygous or wild-type'");

		addAnnotationToChain(CollectionsUtil.createList(new Span(8034, 8052), new Span(8066, 8077)),
				CollectionsUtil.createList(new Span(15930, 15935)), identAnnotations, memberToChainAnnotMap,
				npAnnotations, documentText, factory,
				"adding reference to heterozygous mice that was just removed from a chain about 'heterozygous or wilde-type' mice");

		addAnnotationToChain(CollectionsUtil.createList(new Span(8034, 8052), new Span(8066, 8077)),
				CollectionsUtil.createList(new Span(36877, 36889)), identAnnotations, memberToChainAnnotMap,
				npAnnotations, documentText, factory,
				"adding reference to heterozygous mice that was just removed from a chain about 'heterozygous or wilde-type' mice");

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8169, 8181)),
				CollectionsUtil.createList(new Span(20703, 20712), new Span(20732, 20744)), identAnnotations,
				memberToChainAnnotMap,
				"removing reference to 'increased water intake' from chain about 'water intake'");

		removeAnnotationFromChain(6025, 6064, 7706, 7739, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'phenylalanine at position 204' from chain about 'F204V mutation'");
		removeAnnotationFromChain(6025, 6064, 7502, 7506, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'phenylalanine at position 204' from chain about 'F204V mutation'");

		addAnnotationToChain(7706, 7739, 7502, 7506, identAnnotations, memberToChainAnnotMap, npAnnotations,
				documentText, factory,
				"adding reference to 'F206' that was just removed from a chain about 'F206V mutation'");

		removeAnnotationFromChain(6398, 6427, 6495, 6513, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'mutant protein' from chain about 'MDCK cells'");

		removeAnnotationFromChain(14861, 14887, 15153, 15162, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'forskolin' from chain about 'forskolin stimulation'");

		removeAnnotationFromChain(1860, 1872, 1897, 1900, identAnnotations, memberToChainAnnotMap,
				"'its' refers to 'AVP' not 'AVP receptor'");

		removeAnnotationFromChain(1042, 1046, 41328, 41342, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'wild-type AQP2' from chain about 'AQP2' in general");

		removeAnnotationFromChain(6025, 6064, 15861, 15908, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'animals with a mutation' from chain about the 'mutation' itself");

	}

	private static void curatedChangesFor_16121256(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap, Set<TextAnnotation> npAnnotations,
			String documentText, TextAnnotationFactory factory) {

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(13613, 13615), new Span(13627, 13641)),
				CollectionsUtil.createList(new Span(13676, 13696)), identAnnotations, memberToChainAnnotMap,
				"remove 'the predominant peak' -- it will be replaced with 'the predominant peak ... in humans'");

		addAnnotationToChain(CollectionsUtil.createList(new Span(13613, 13615), new Span(13627, 13641)),
				CollectionsUtil.createList(new Span(13676, 13696), new Span(13718, 13727)), identAnnotations,
				memberToChainAnnotMap, npAnnotations, documentText, factory,
				"adding new annotation for 'the predominant peak .. in humans'");

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(13676, 13696)),
				CollectionsUtil.createList(new Span(13676, 13696)), identAnnotations, memberToChainAnnotMap,
				"remove 'the predominant peak' -- it will be replaced with 'the predominant peak ... in the mouse'");

		addAnnotationToChain(CollectionsUtil.createList(new Span(13613, 13615), new Span(13627, 13641)),
				CollectionsUtil.createList(new Span(13676, 13696), new Span(13737, 13749)), identAnnotations,
				memberToChainAnnotMap, npAnnotations, documentText, factory,
				"adding new annotation for 'the predominant peak .. in the mouse'");

		removeAnnotationFromChain(7444, 7451, 11097, 11104, identAnnotations, memberToChainAnnotMap,
				"'MCAD+/+' is a reference to 'pups' in this case");
	}

	private static void curatedChangesFor_16216087(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap, Set<TextAnnotation> npAnnotations,
			String documentText, TextAnnotationFactory factory) {

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(1286, 1295), new Span(1306, 1320)),
				CollectionsUtil.createList(new Span(27623, 27632), new Span(27652, 27660)), identAnnotations,
				memberToChainAnnotMap,
				"removing reference to 'olfactory learning' from chain about 'olfactory discrimination'");

		// there are two chains that start with 'simple' so we remove the
		// annotation from both, then add it back to the proper chain
		removeAnnotationFromChain(13902, 13930, 13902, 13930, identAnnotations, memberToChainAnnotMap,
				"removed 'difficult' from 'simple' chain. There are not the same things; there are two identity chains that start with 'simple...'. One is correct, the other is not. However, we do not know which identity chain will be processed first, so we remove from both and then add back to the proper chain afterwards.");

		removeAnnotationFromChain(13902, 13930, 13902, 13930, identAnnotations, memberToChainAnnotMap,
				"removed 'difficult' from 'simple' chain. There are not the same things; ; there are two identity chains that start with 'simple...'. One is correct, the other is not. However, we do not know which identity chain will be processed first, so we remove from both and then add back to the proper chain afterwards.");

		addAnnotationToChain(29651, 29695, 13902, 13930, identAnnotations, memberToChainAnnotMap, npAnnotations,
				documentText, factory,
				"adding 'simple...' back to proper chain. Had to remove from both b/c there were 2 identity chains that started with the annotation to-be-removed.");

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(721, 737)),
				CollectionsUtil.createList(new Span(43430, 43439), new Span(43469, 43475)), identAnnotations,
				memberToChainAnnotMap, "removing reference to 'olfactory memory' from chain about 'memory'");

		// the 'difficult' annotation is the head of two identity chains, so we
		// do this twice, then add it back to the proper chain.
		removeAnnotationFromChain(14148, 14241, 14148, 14241, identAnnotations, memberToChainAnnotMap,
				"removed 'difficult' from 'simple' chain. There are not the same things; there are two identity chains that start with 'difficult...'. One is correct, the other is not. However, we do not know which identity chain will be processed first, so we remove from both and then add back to the proper chain afterwards.");

		removeAnnotationFromChain(14148, 14241, 14148, 14241, identAnnotations, memberToChainAnnotMap,
				"removed 'difficult' from 'simple' chain. There are not the same things.; there are two identity chains that start with 'difficult...'. One is correct, the other is not. However, we do not know which identity chain will be processed first, so we remove from both and then add back to the proper chain afterwards.");

		addAnnotationToChain(14637, 14747, 14148, 14241, identAnnotations, memberToChainAnnotMap, npAnnotations,
				documentText, factory,
				"adding 'difficult...' back to proper chain. Had to remove from both b/c there were 2 identity chains that started with the annotation to-be-removed.");

	}

	private static void curatedChangesFor_16221973(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap, Set<TextAnnotation> npAnnotations,
			String documentText, TextAnnotationFactory factory) {

		removeAnnotationFromChain(2331, 2340, 37564, 37566, identAnnotations, memberToChainAnnotMap,
				"removed 'we' from chain about 'the mouse'");

		// double removal required b/c two identity chains start with the
		// annotation to-be-removed
		removeAnnotationFromChain(15938, 15954, 15938, 15954, identAnnotations, memberToChainAnnotMap,
				"removed 'functional MTF-1' from chain about 'protein extract from control mouse'");

		removeAnnotationFromChain(15938, 15954, 15938, 15954, identAnnotations, memberToChainAnnotMap,
				"removed 'functional MTF-1' from chain about 'protein extract from control mouse'");

		removeAnnotationFromChain(16715, 16767, 27963, 27978, identAnnotations, memberToChainAnnotMap,
				"removed 'functional MTF-1' from chain about 'protein extract from control mouse'");

		addAnnotationToChain(16610, 16626, 15938, 15954, identAnnotations, memberToChainAnnotMap, npAnnotations,
				documentText, factory,
				"adding 'functional MTF-1' back to proper chain. Had to remove from both b/c there were 2 identity chains that started with the annotation to-be-removed.");

	}

	private static void curatedChangesFor_16255782(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap, Set<TextAnnotation> npAnnotations,
			String documentText, TextAnnotationFactory factory) {

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(407, 511), new Span(519, 567)),
				CollectionsUtil.createList(new Span(17461, 17489), new Span(17493, 17509)), identAnnotations,
				memberToChainAnnotMap,
				"removing reference to 'antioxidant and dna repair genes' from chain about only 'antioxidant genes'");

		// double remove then re-add
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(478, 511), new Span(519, 536)),
				CollectionsUtil.createList(new Span(478, 511), new Span(519, 536)), identAnnotations,
				memberToChainAnnotMap, "removing reference to 'BC individuals' from chain about  'non-BC individuals'");

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(478, 511), new Span(519, 536)),
				CollectionsUtil.createList(new Span(478, 511), new Span(519, 536)), identAnnotations,
				memberToChainAnnotMap, "removing reference to 'BC individuals' from chain about 'non-BC individuals'");

		addAnnotationToChain(CollectionsUtil.createList(new Span(3722, 3744)),
				CollectionsUtil.createList(new Span(478, 511), new Span(519, 536)), identAnnotations,
				memberToChainAnnotMap, npAnnotations, documentText, factory,
				"adding reference to 'BC individuals' that was just removed from a chain about 'non-BC individuals' mice");

		removeAnnotationFromChain(124, 163, 12764, 12778, identAnnotations, memberToChainAnnotMap,
				"removed reference to specific 25 BC individuals used in study from chain about general BC individuals");

		removeAnnotationFromChain(1376, 1407, 26705, 26716, identAnnotations, memberToChainAnnotMap,
				"removed reference to 'samples' from chain about general 'individuals'");

		removeAnnotationFromChain(1376, 1407, 10897, 10911, identAnnotations, memberToChainAnnotMap,
				"removed reference to 'samples' from chain about general 'individuals'");

		addAnnotationToChain(595, 616, 10897, 10911, identAnnotations, memberToChainAnnotMap, npAnnotations,
				documentText, factory, "adding 'samples' to chain about 'samples'");

		removeAnnotationFromChain(10737, 10798, 14115, 14156, identAnnotations, memberToChainAnnotMap,
				"removed reference to '4 genes' from chain about general '16 genes'");
	}

	private static void curatedChangesFor_16279840(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {
		// no manual changes
	}

	private static void curatedChangesFor_16362077(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(384, 402, 23040, 23045, identAnnotations, memberToChainAnnotMap,
				"'their' refers to MEFs not mice");

		removeAnnotationFromChain(384, 402, 23006, 23022, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'Sam68−/− animals' from chain that is about '12-month-old animals'");
		removeAnnotationFromChain(384, 402, 22585, 22605, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'Sam68−/− animals' from chain that is about '12-month-old animals'");
		removeAnnotationFromChain(384, 402, 22616, 22621, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'Sam68−/− animals' from chain that is about '12-month-old animals'");

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(1116, 1124), new Span(1138, 1149)),
				CollectionsUtil.createList(new Span(7107, 7128)), identAnnotations, memberToChainAnnotMap,
				"keeping 'their wild-type littermates' in chain about older mice given context in document");

		removeAnnotationFromChain(453, 487, 7107, 7128, identAnnotations, memberToChainAnnotMap,
				"keeping 'wild-type littermates' in chain about wild-type mice (no age specified) given context in document");

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(9501, 9511), new Span(9520, 9537)),
				CollectionsUtil.createList(new Span(9625, 9631)), identAnnotations, memberToChainAnnotMap,
				"removing references to 'SC-333' from chain about 'SC-333 antibodies'");
		removeAnnotationFromChain(73, 77, 6623, 6654, identAnnotations, memberToChainAnnotMap,
				"removing references to 'the generation of mice' from chain about 'bone'");

	}

	private static void curatedChangesFor_16433929(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {
		// no manual changes
	}

	private static void curatedChangesFor_16462940(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {
		// no manual changes

	}

	private static void curatedChangesFor_16504143(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(190, 192, 190, 192, identAnnotations, memberToChainAnnotMap,
				"'It' refers to 'ADAM11'");

	}

	private static void curatedChangesFor_16504174(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {
		removeAnnotationFromChain(317, 383, 394, 399, identAnnotations, memberToChainAnnotMap,
				"'their' refers to 'five pseudogenes'");

	}

	private static void curatedChangesFor_16507151(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(594, 599)),
				CollectionsUtil.createList(new Span(10464, 10469), new Span(10480, 10491)), identAnnotations,
				memberToChainAnnotMap, "removing reference to 'female mice' from chain about 'NFR/N'");

	}

	private static void curatedChangesFor_16539743(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		// The [7428..7480] 'a SAM domain related to that of polyhomeotic
		// protein' is a mixture of mr-s domains and others. It is mostly about
		// mr-s domains however, so we remove reference not to mr-s domains

		removeAnnotationFromChain(7428, 7480, 36579, 36593, identAnnotations, memberToChainAnnotMap,
				"remove reference to non mr-s SAM domains (this one is about H-L(3)MBT protein) as this chain is mostly references to 'SAM domain of mr-s'");

		removeAnnotationFromChain(7428, 7480, 7428, 7480, identAnnotations, memberToChainAnnotMap,
				"remove reference to non mr-s SAM domains as this chain is mostly references to 'SAM domain of mr-s'");

		removeAnnotationFromChain(1336, 1357, 48198, 48234, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'transcriptional activity' from chain about 'transcription'");

		removeAnnotationFromChain(18709, 18733, 43571, 43573, identAnnotations, memberToChainAnnotMap,
				"'it' refers to the pGBKT7 vector");

		removeAnnotationFromChain(1635, 1648, 25202, 25209, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'HEK293T' from chain about 'HEK293T cells'");

		removeAnnotationFromChain(1667, 1677, 28425, 28427, identAnnotations, memberToChainAnnotMap,
				"'it' refers to the 'luciferase activity'");

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(22309, 22316)),
				CollectionsUtil.createList(new Span(22697, 22708), new Span(22745, 22749), new Span(22806, 22813)),
				identAnnotations, memberToChainAnnotMap,
				"removing reference to 'Flag-ΔSAM' from chain about 'ΔSAM-HA'");

		removeAnnotationFromChain(8279, 8282, 35205, 35208, identAnnotations, memberToChainAnnotMap,
				"'its' refers to the 'ph'");
		removeAnnotationFromChain(2275, 2295, 31877, 31883, identAnnotations, memberToChainAnnotMap,
				"keeping reference to 'retina' in chain about 'mouse retina'");

	}

	private static void curatedChangesFor_16579849(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(23359, 23401, 23979, 24008, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'response element' from chain about 'transactivator'");

		removeAnnotationFromChain(35, 102, 39192, 39205, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'these animals' from chain about 'animal models'");

	}

	private static void curatedChangesFor_16628246(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap, Set<TextAnnotation> npAnnotations,
			String documentText, TextAnnotationFactory factory) {

		// remove twice then re-add
		removeAnnotationFromChain(12391, 12397, 12391, 12397, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'Line 1' from chain about 'Sine B1'");
		removeAnnotationFromChain(12391, 12397, 12391, 12397, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'Line 1' from chain about 'Sine B1'");
		addAnnotationToChain(12488, 12494, 12391, 12397, identAnnotations, memberToChainAnnotMap, npAnnotations,
				documentText, factory, "remove reference to 'Line 1' from chain about 'Sine B1'");

		removeAnnotationFromChain(16453, 16477, 58408, 58413, identAnnotations, memberToChainAnnotMap,
				"'their' refers to 'wild-type or Atrxnull'");
		removeAnnotationFromChain(16453, 16477, 57514, 57519, identAnnotations, memberToChainAnnotMap,
				"'their' refers to 'wild-type or Atrxnull'");
		removeAnnotationFromChain(16453, 16477, 60039, 60044, identAnnotations, memberToChainAnnotMap,
				"'their' refers to 'wild-type or Atrxnull'");

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(8805, 8810), new Span(8905, 8913)),
				CollectionsUtil.createList(new Span(9366, 9385)), identAnnotations, memberToChainAnnotMap,
				"remove reference of alleles from chain about cells");

		removeAnnotationFromChain(49033, 49040, 49059, 49064, identAnnotations, memberToChainAnnotMap,
				"'their' does not refer to embryos");

		removeAnnotationFromChain(15436, 15450, 49059, 49064, identAnnotations, memberToChainAnnotMap,
				"'their' does not refer to deciduas");

		// remove twice then readd
		removeAnnotationFromChain(10237, 10247, 10237, 10247, identAnnotations, memberToChainAnnotMap, "Figure S1B");
		removeAnnotationFromChain(10237, 10247, 10237, 10247, identAnnotations, memberToChainAnnotMap, "Figure S1B");

		addAnnotationToChain(19087, 19097, 10237, 10247, identAnnotations, memberToChainAnnotMap, npAnnotations,
				documentText, factory, "re-add to Figure S1B chain");

	}

	private static void curatedChangesFor_16670015(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {
		// no manual changes
	}

	private static void curatedChangesFor_16700629(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap, Set<TextAnnotation> npAnnotations,
			String documentText, TextAnnotationFactory factory) {

		// double remove then re-add
		removeAnnotationFromChain(4711, 4729, 4711, 4729, identAnnotations, memberToChainAnnotMap,
				"remove references to 'expression of Sox9' from chain about 'complete sex reversal'");

		removeAnnotationFromChain(4711, 4729, 4711, 4729, identAnnotations, memberToChainAnnotMap,
				"remove references to 'expression of Sox9' from chain about 'complete sex reversal'");

		// double remove then re-add
		removeAnnotationFromChain(5343, 5358, 5343, 5358, identAnnotations, memberToChainAnnotMap,
				"remove references to 'expression of Sox9' from chain about 'complete sex reversal'");

		removeAnnotationFromChain(5343, 5358, 5343, 5358, identAnnotations, memberToChainAnnotMap,
				"remove references to 'expression of Sox9' from chain about 'complete sex reversal'");

		removeAnnotationFromChain(5374, 5395, 7361, 7379, identAnnotations, memberToChainAnnotMap,
				"remove references to 'expression of Sox9' from chain about 'complete sex reversal'");

		removeAnnotationFromChain(5374, 5395, 15563, 15585, identAnnotations, memberToChainAnnotMap,
				"remove references to 'expression of Sox9' from chain about 'complete sex reversal'");

		/*
		 * can't re-add this one b/c it's in another chain, but they will get merged so it's ok.
		 */
		// addAnnotationToChain(11379,11394, 7361,7379, identAnnotations,
		// memberToChainAnnotMap, npAnnotations,
		// "re-add to 'expression of Sox9' chain");

		addAnnotationToChain(11379, 11394, 5343, 5358, identAnnotations, memberToChainAnnotMap, npAnnotations,
				documentText, factory, "re-add to 'expression of Sox9' chain");

		addAnnotationToChain(5343, 5358, 4711, 4729, identAnnotations, memberToChainAnnotMap, npAnnotations,
				documentText, factory, "re-add to 'expression of Sox9' chain");

	}

	private static void curatedChangesFor_16870721(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(8933, 8941, 27639, 27645, identAnnotations, memberToChainAnnotMap,
				"remove references to 'p53GFP' from chain about 'p53ΔPGFP'");

		removeAnnotationFromChain(6336, 6386, 6665, 6667, identAnnotations, memberToChainAnnotMap,
				"'it' refers to 'loxP257 spacer sequence");

		removeAnnotationFromChain(3186, 3196, 27349, 27362, identAnnotations, memberToChainAnnotMap,
				"remove reference to specific KO allele from chain about general knockout alleles");

		removeAnnotationFromChain(0, 9, 421, 459, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'RMCE' from chain about 'RMCE-ASAP'");

	}

	private static void curatedChangesFor_17002498(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(5851, 5857, 57070, 57176, identAnnotations, memberToChainAnnotMap,
				"this phrase refers to the animals not the allele");

		removeAnnotationFromChain(19926, 19969, 26354, 26387, identAnnotations, memberToChainAnnotMap,
				"removing reference to normal mice from chain about mutant and normal mice");

		removeAnnotationFromChain(19926, 19969, 26762, 26795, identAnnotations, memberToChainAnnotMap,
				"removing reference to normal mice from chain about mutant and normal mice");

		removeAnnotationFromChain(19926, 19969, 22028, 22041, identAnnotations, memberToChainAnnotMap,
				"removing reference to mutant mice from chain about mutant and normal mice");

		removeAnnotationFromChain(28093, 28115, 57225, 57227, identAnnotations, memberToChainAnnotMap,
				"removing reference to mutant littermate from chain about normal littermates");

	}

	private static void curatedChangesFor_17020410(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(14972, 14986), new Span(15009, 15019)),
				CollectionsUtil.createList(new Span(14972, 14986), new Span(15009, 15019)), identAnnotations,
				memberToChainAnnotMap, "remove reference of '20-mo-old male' from chain about 'XpdTTD/TTD'");

		removeAnnotationFromChain(12553, 12554, 12722, 12723, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'Figure 2E' from chain about 'Figure 2D'");

		removeAnnotationFromChain(95, 118, 17275, 17297, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'compound heterozygotes' from chain about 'compound heterozygosity'");

	}

	private static void curatedChangesFor_17022820(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(11289, 11302, 20473, 20481, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'collagen' from chain about 'collagen area'");

		removeAnnotationFromChain(1875, 1901, 3993, 4014, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'C57BL/6J (B6) and A/J' from chain about 'B6-Chr5A/J and B6-Chr17A/J'");

		removeAnnotationFromChain(700, 713, 12434, 12443, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'A/J mouse' from chain about 'C57Bl/6J mice'");

		removeAnnotationFromChain(6440, 6465, 6455, 6465, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'this study' from chain about 'results of this study'");
	}

	private static void curatedChangesFor_17069463(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap, Set<TextAnnotation> npAnnotations,
			String documentText, TextAnnotationFactory factory) {

		removeAnnotationFromChain(27, 50, 39423, 39441, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'HKI activity/level' from chain about 'HKI activity'");

		removeAnnotationFromChain(21784, 21801, 39423, 39441, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'HKI activity/level' from chain about 'HKI level'");

		// HKI activity
		addAnnotationToChain(27, 50, 39423, 39435, identAnnotations, memberToChainAnnotMap, npAnnotations, documentText,
				factory, "adding new annotation for 'HKI activity'");

		// HKI .. level
		addAnnotationToChain(CollectionsUtil.createList(new Span(21784, 21801)),
				CollectionsUtil.createList(new Span(39423, 39426), new Span(39436, 39441)), identAnnotations,
				memberToChainAnnotMap, npAnnotations, documentText, factory,
				"adding new annotation for 'HKI .. level'");

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(9135, 9141), new Span(9154, 9161)),
				CollectionsUtil.createList(new Span(14125, 14141)), identAnnotations, memberToChainAnnotMap,
				"remove reference of 'Cox11 and RanBP2' from chain about 'RanBP2 and HKI'");

	}

	private static void curatedChangesFor_17078885(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(5212, 5233, 16421, 16446, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'the Alk2/Wnt1-Cre mutants' from chain about 'the Alk5/Wnt1-Cre mutants'");

		removeAnnotationFromChain(26905, 26959, 26991, 27002, identAnnotations, memberToChainAnnotMap,
				"removing reference to 'this result' as the other chain member is not the complete result");

		removeAnnotationFromChain(17118, 17139, 29562, 29575, identAnnotations, memberToChainAnnotMap,
				"'these mutants' refers only to Alk5 mutants");

	}

	private static void curatedChangesFor_17083276(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(0, 5, 23193, 23196, identAnnotations, memberToChainAnnotMap,
				"'its' refers to 'heterochromatin'");

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(37205, 37219), new Span(37234, 37239)),
				CollectionsUtil.createList(new Span(38119, 38140)), identAnnotations, memberToChainAnnotMap,
				"remove reference to 'control siRNA vectors' from chain about 'control siRNA'");

		removeAnnotationFromChain(13267, 13276, 34227, 34228, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'Figure 1A' from chain about 'Figure 4A'");

	}

	private static void curatedChangesFor_17194222(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {
		// no manual changes required

	}

	private static void curatedChangesFor_17244351(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(544, 585, 9440, 9499, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'the absolute difference between the two groups exceeded 100' from chain about 'stains'");

	}

	private static void curatedChangesFor_17425782(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(18887, 18906, 20468, 20482, identAnnotations, memberToChainAnnotMap,
				"remove reference to 'the Pygo2 gene' from chain about 'loss of Pygo2 alone'");

	}

	private static void curatedChangesFor_17447844(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(8154, 8160, 15902, 15908, identAnnotations, memberToChainAnnotMap,
				"'testes' refers to the Dmrt7 testes here.");

		removeAnnotationFromChain(35529, 35577, 35587, 35599, identAnnotations, memberToChainAnnotMap,
				"'this process' refers to 'XY body internalization' .");

		removeAnnotationFromChain(46392, 46425, 51697, 51708, identAnnotations, memberToChainAnnotMap,
				"'this allele' refers to 'the targeted allele Dmrtneo' .");

	}

	private static void curatedChangesFor_17590087(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(4734, 4746, 5295, 5376, identAnnotations, memberToChainAnnotMap,
				"this is not the phenotype it's referencing");

		removeAnnotationFromChain(0, 17, 6383, 6388, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'ITPR1' from chain about 'mutation at ITPR1'");

		removeAnnotationFromChain(7934, 7970, 9157, 9195, identAnnotations, memberToChainAnnotMap,
				"remove reference of 'the newly defined centromeric boundary' from chain about 'centromeric side'");

		removeAnnotationFromChain(CollectionsUtil.createList(new Span(9289, 9300), new Span(9309, 9313)),
				CollectionsUtil.createList(new Span(30820, 30839)), identAnnotations, memberToChainAnnotMap,
				"remove reference of 'primer T3f and C11r' from chain about only 'primer C11r'");

	}

	private static void curatedChangesFor_17608565(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {

		removeAnnotationFromChain(4034, 4050, 26832, 26835, identAnnotations, memberToChainAnnotMap,
				"'its' refers to 'Camk2a+ soma'");

		removeAnnotationFromChain(3710, 3713, 23913, 23920, identAnnotations, memberToChainAnnotMap,
				"'INL' refers to 'Rb KO INL'");
		removeAnnotationFromChain(3710, 3713, 23977, 23984, identAnnotations, memberToChainAnnotMap,
				"'INL' refers to 'Rb KO INL'");

		removeAnnotationFromChain(163, 165, 18240, 18243, identAnnotations, memberToChainAnnotMap,
				"'its' refers to 'E2f1'");

	}

	private static void curatedChangesFor_17696610(Set<TextAnnotation> identAnnotations,
			Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap) {
		// no manual changes required
	}

	/**
	 * Helper method to add an annotation to a chain based on the annotation and chain character
	 * offsets
	 * 
	 * @param chainStart
	 * @param chainEnd
	 * @param annotStart
	 * @param annotEnd
	 * @param identAnnotations
	 * @param memberToChainAnnotMap
	 * @param npAnnotations
	 * @param documentText
	 * @param factory
	 * @param reason
	 */
	private static void addAnnotationToChain(int chainStart, int chainEnd, int annotStart, int annotEnd,
			Set<TextAnnotation> identAnnotations, Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap,
			Set<TextAnnotation> npAnnotations, String documentText, TextAnnotationFactory factory, String reason) {
		addAnnotationToChain(CollectionsUtil.createList(new Span(chainStart, chainEnd)),
				CollectionsUtil.createList(new Span(annotStart, annotEnd)), identAnnotations, memberToChainAnnotMap,
				npAnnotations, documentText, factory, reason);
	}

	private static void addAnnotationToChain(List<Span> identChainSpans, List<Span> annotToAddSpans,
			Set<TextAnnotation> identAnnotations, Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap,
			Set<TextAnnotation> npAnnotations, String documentText, TextAnnotationFactory factory, String reason) {
		boolean foundChain = false;
		for (TextAnnotation chainTa : identAnnotations) {
			if (chainTa.getClassMention().getMentionName().equalsIgnoreCase(CoNLLCoref2012DocumentReader.IDENTITY_CHAIN)
					&& chainTa.getSpans().equals(identChainSpans)) {
				foundChain = true;
				ComplexSlotMention csm = chainTa.getClassMention().getComplexSlotMentionByName(
						CoNLLCoref2012DocumentReader.IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT);
				TextAnnotation taToAdd = null;
				for (TextAnnotation npAnnot : npAnnotations) {
					if (npAnnot.getSpans().equals(annotToAddSpans)) {
						taToAdd = npAnnot;
						break;
					}
				}
				if (taToAdd == null) {
					taToAdd = CoNLLCoref2012DocumentWriter.createNpAnnotation(factory, documentText, annotToAddSpans,
							IncludeCorefType.IDENT, npAnnotations);
				}

				/*
				 * check to see if the taToAdd is already member of a different chain. throw an
				 * error if it is
				 */
				Set<TextAnnotation> otherChains = memberToChainAnnotMap.get(taToAdd);
				if (otherChains != null && otherChains.size() > 0) {
					throw new IllegalStateException("Cannot add annotation that is already a member of a different ("
							+ otherChains.size() + ") chain.\n" + "Annotation:\n"
							+ CoNLLCoref2012DocumentWriter.toLogString(taToAdd) + "\nSample other chain:\n"
							+ CoNLLCoref2012DocumentWriter.toLogString(otherChains.iterator().next()));
				}

				logger.info("#### Curated addition of annotation to IDENTITY chain\nAdded annotation: "
						+ CoNLLCoref2012DocumentWriter.toLogString(taToAdd) + "\nReasoning: " + reason);
				logger.info("Added to Chain:\n" + CoNLLCoref2012DocumentWriter.toLogString("> ", chainTa) + "\n");

				/* update member-to-chain map */
				CollectionsUtil.addToOne2ManyUniqueMap(taToAdd, chainTa, memberToChainAnnotMap);

				/* update chain */
				csm.getClassMentions().add(taToAdd.getClassMention());
				/*
				 * the annotation added may be the first in the chain, so update the identity chain
				 * span and covered text
				 */
				List<TextAnnotation> chainMembers = new ArrayList<TextAnnotation>();
				for (ClassMention cm : csm.getClassMentions()) {
					chainMembers.add(cm.getTextAnnotation());
				}
				Collections.sort(chainMembers, TextAnnotation.BY_SPAN());
				chainTa.setSpans(chainMembers.get(0).getSpans());
				chainTa.setCoveredText(chainMembers.get(0).getCoveredText());

				/* break since we've updated the chain */
				break;
			}
		}
		if (!foundChain) {
			throw new IllegalStateException("Unable to find chain (" + identChainSpans + ") in document: "
					+ identAnnotations.iterator().next().getDocumentID());
		}
	}

	/**
	 * Helper method to remove an annotation from a chain based on character offsets for both the
	 * annotation and the beginning of the chains
	 * 
	 * @param chainStart
	 * @param chainEnd
	 * @param annotStart
	 * @param annotEnd
	 * @param identAnnotations
	 * @param memberToChainAnnotMap
	 * @param reason
	 */
	private static void removeAnnotationFromChain(int chainStart, int chainEnd, int annotStart, int annotEnd,
			Set<TextAnnotation> identAnnotations, Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap,
			String reason) {
		removeAnnotationFromChain(CollectionsUtil.createList(new Span(chainStart, chainEnd)),
				CollectionsUtil.createList(new Span(annotStart, annotEnd)), identAnnotations, memberToChainAnnotMap,
				reason);
	}

	private static void removeAnnotationFromChain(List<Span> identChainSpans, List<Span> annotToRemoveSpans,
			Set<TextAnnotation> identAnnotations, Map<TextAnnotation, Set<TextAnnotation>> memberToChainAnnotMap,
			String reason) {
		boolean foundChain = false;
		for (TextAnnotation chainTa : identAnnotations) {
			if (chainTa.getClassMention().getMentionName().equalsIgnoreCase(CoNLLCoref2012DocumentReader.IDENTITY_CHAIN)
					&& chainTa.getSpans().equals(identChainSpans)) {
				foundChain = true;
				ComplexSlotMention csm = chainTa.getClassMention().getComplexSlotMentionByName(
						CoNLLCoref2012DocumentReader.IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT);
				Set<TextAnnotation> annotsToRemove = new HashSet<TextAnnotation>();
				for (ClassMention cm : csm.getClassMentions()) {
					if (cm.getTextAnnotation().getSpans().equals(annotToRemoveSpans)) {
						annotsToRemove.add(cm.getTextAnnotation());
					}
				}
				if (annotsToRemove.isEmpty()) {
					throw new IllegalArgumentException("Unable to find chain annotation to remove ("
							+ annotToRemoveSpans + ") from chain (" + identChainSpans + ") for document: "
							+ identAnnotations.iterator().next().getDocumentID());
				}

				logger.info("#### Curated removal of annotation from IDENTITY chain\nRemoved annotation(s): "
						+ CoNLLCoref2012DocumentWriter.toLogString(annotsToRemove) + "\nReasoning: " + reason);
				logger.info("Removed from Chain:\n" + CoNLLCoref2012DocumentWriter.toLogString("> ", chainTa) + "\n");

				/*
				 * Note: for some reason, set.remove(chainTa) was not working in some cases. Not
				 * sure why. Using list.remove(chainTa) appears to work as expected.
				 */
				for (TextAnnotation taToRemove : annotsToRemove) {
					List<TextAnnotation> chainList = new ArrayList<TextAnnotation>(
							memberToChainAnnotMap.get(taToRemove));
					if (!chainList.remove(chainTa)) {
						logger.error("Chain annotation:\n" + CoNLLCoref2012DocumentWriter.toLogString("", chainTa));
						for (TextAnnotation c : memberToChainAnnotMap.get(taToRemove)) {
							logger.error("== Chain set member:\n" + CoNLLCoref2012DocumentWriter.toLogString("", c));
						}
						throw new IllegalStateException("Unable to remove chain annotation from chain set.");
					}

					memberToChainAnnotMap.put(taToRemove, new HashSet<TextAnnotation>(chainList));
					if (chainList.size() > 0) {
						logger.info("Annotation also a member of chain(s):");
						for (TextAnnotation chain : chainList) {
							logger.info(CoNLLCoref2012DocumentWriter.toLogString("> ", chain) + "\n");
						}
					} else {
						logger.info("Annotation not a member of any other chain.");
					}

					csm.getClassMentions().remove(taToRemove.getClassMention());
					/*
					 * if the annotation removed was the first in the chain, then the identity chain
					 * span and covered text need to be updated
					 */
					if (taToRemove.getSpans().equals(chainTa.getSpans())) {
						List<TextAnnotation> chainMembers = new ArrayList<TextAnnotation>();
						for (ClassMention cm : csm.getClassMentions()) {
							chainMembers.add(cm.getTextAnnotation());
						}
						Collections.sort(chainMembers, TextAnnotation.BY_SPAN());
						chainTa.setSpans(chainMembers.get(0).getSpans());
						chainTa.setCoveredText(chainMembers.get(0).getCoveredText());
					}
				}
				/* break since we've updated the chain */
				break;

			}
		}
		if (!foundChain) {
			throw new IllegalStateException("Unable to find chain (" + identChainSpans + ") in document: "
					+ identAnnotations.iterator().next().getDocumentID());
		}
	}

	/**
	 * @param apposAnnotations
	 * @param npAnnotations
	 * @param documentText
	 * @param factory
	 * @returnb a set of APPOS relation annotations that have been cleaned
	 */
	private static List<TextAnnotation> cleanApposRelations(Set<TextAnnotation> apposAnnotations,
			Set<TextAnnotation> npAnnotations, String documentText, TextAnnotationFactory factory) {

		List<TextAnnotation> cleanApposAnnots = new ArrayList<TextAnnotation>();
		Map<List<Span>, TextAnnotation> spanToNounPhraseAnnotMap = new HashMap<List<Span>, TextAnnotation>();
		for (TextAnnotation npAnnot : npAnnotations) {
			spanToNounPhraseAnnotMap.put(npAnnot.getSpans(), npAnnot);
		}

		for (TextAnnotation apposAnnot : apposAnnotations) {
			ComplexSlotMention headCsm = apposAnnot.getClassMention()
					.getComplexSlotMentionByName(CoNLLCoref2012DocumentReader.APPOS_HEAD_SLOT);

			ComplexSlotMention attributeCsm = apposAnnot.getClassMention()
					.getComplexSlotMentionByName(CoNLLCoref2012DocumentReader.APPOS_ATTRIBUTES_SLOT);

			if (headCsm.getClassMentions().isEmpty()) {
				logger.info("#### Auto-populating APPOS Head slot with noun phrase");
				logger.info("Before: " + CoNLLCoref2012DocumentWriter.toLogString(apposAnnot));
				TextAnnotation npHeadAnnot = CoNLLCoref2012DocumentWriter.findOrCreateCoveringNpAnnot(factory,
						documentText, spanToNounPhraseAnnotMap, apposAnnot, headCsm, IncludeCorefType.APPOS,
						npAnnotations);
				headCsm.addClassMention(npHeadAnnot.getClassMention());
				logger.info("After: " + CoNLLCoref2012DocumentWriter.toLogString(apposAnnot));
			}

			if (!attributeCsm.getClassMentions().isEmpty()) {
				Set<TextAnnotation> headAnnots = new HashSet<TextAnnotation>();
				Set<TextAnnotation> attribAnnots = new HashSet<TextAnnotation>();
				for (ClassMention cm : headCsm.getClassMentions()) {
					headAnnots.add(cm.getTextAnnotation());
				}
				for (ClassMention cm : attributeCsm.getClassMentions()) {
					attribAnnots.add(cm.getTextAnnotation());
				}

				/* make sure head and attribute slot fillers are different */
				if (!headAnnots.equals(attribAnnots)) {
					cleanApposAnnots.add(apposAnnot);
				} else {
					logger.info("#### Excluding APPOS relation due to matching head and attributes\n"
							+ CoNLLCoref2012DocumentWriter.toLogString(apposAnnot));
				}
			} else {
				logger.info("#### Excluding APPOS relation due to missing attributes slot\n"
						+ CoNLLCoref2012DocumentWriter.toLogString(apposAnnot));
			}
		}
		return cleanApposAnnots;
	}

	/**
	 * @param td
	 * @param npAnnotations
	 * @param factory
	 * @return add missing NPs if there are any and remove chains of length 1
	 */
	private static Set<TextAnnotation> cleanIdentityChains_step1(TextDocument td, Set<TextAnnotation> npAnnotations,
			TextAnnotationFactory factory) {
		Map<TextAnnotation, Set<TextAnnotation>> chainAnnotToMemberAnnotsMap = CoNLLCoref2012DocumentWriter
				.getCoreferenceChains(factory, td.getText(), td.getAnnotations(), npAnnotations);

		Set<Set<TextAnnotation>> identChains = new HashSet<Set<TextAnnotation>>(chainAnnotToMemberAnnotsMap.values());
		Set<TextAnnotation> cleanIdentAnnots = formChainsFromSets(factory, td.getText(), identChains);
		return cleanIdentAnnots;
	}

	/**
	 * @param identAnnots
	 * @param npAnnotations
	 * @param documentText
	 * @param factory
	 * @return merge chains with common members
	 */
	private static Set<TextAnnotation> cleanIdentityChains_step2(Collection<TextAnnotation> identAnnots,
			Set<TextAnnotation> npAnnotations, String documentText, TextAnnotationFactory factory) {

		/*
		 * not redundant here b/c there may be some single length 'chains' to remove due to the
		 * manual curation step
		 */
		Map<TextAnnotation, Set<TextAnnotation>> chainAnnotToMemberAnnotsMap = CoNLLCoref2012DocumentWriter
				.getCoreferenceChains(factory, documentText, identAnnots, npAnnotations);

		Set<Set<TextAnnotation>> identChains = new HashSet<Set<TextAnnotation>>(chainAnnotToMemberAnnotsMap.values());
		/*
		 * if there is an annotation that is a member of >1 chains, then those chains should be
		 * combined - this step fixes some annotation errors. Ideally this step would not change the
		 * annotation at all. This step is only relevant for IDENTITY chains.
		 */
		identChains = CoNLLCoref2012DocumentWriter.mergeChainsIfSharedAnnotation(identChains,
				MatchDueTo.SHARED_MENTION);
		Set<TextAnnotation> cleanIdentAnnots = formChainsFromSets(factory, documentText, identChains);
		return cleanIdentAnnots;
	}

	/**
	 * @param factory
	 * @param documentText
	 * @param identChains
	 * @return identity chain annots formed from the sets of chain member annotations
	 */
	private static Set<TextAnnotation> formChainsFromSets(TextAnnotationFactory factory, String documentText,
			Set<Set<TextAnnotation>> identChains) {
		Set<TextAnnotation> identityChainAnnots = new HashSet<TextAnnotation>();
		for (Set<TextAnnotation> chainMembers : identChains) {
			List<TextAnnotation> taList = new ArrayList<TextAnnotation>(chainMembers);
			Collections.sort(taList, TextAnnotation.BY_SPAN());
			TextAnnotation identChainAnnot = factory.createAnnotation(taList.get(0).getSpans(), documentText,
					new DefaultClassMention(CoNLLCoref2012DocumentReader.IDENTITY_CHAIN));

			ComplexSlotMention csm = new DefaultComplexSlotMention(
					CoNLLCoref2012DocumentReader.IDENTITY_CHAIN_COREFERRING_STRINGS_SLOT);
			identChainAnnot.getClassMention().addComplexSlotMention(csm);

			for (TextAnnotation chainMember : taList) {
				csm.addClassMention(chainMember.getClassMention());
			}
			identityChainAnnots.add(identChainAnnot);
		}
		return identityChainAnnots;
	}

	/**
	 * @param annotations
	 * @param documentText
	 * @return the input list of annotations with any leading/trailing spaces removed and the spans
	 *         updated accordingly
	 */
	private static List<TextAnnotation> trimAnnotations(List<TextAnnotation> annotations, String documentText) {
		List<TextAnnotation> updatedAnnotations = new ArrayList<TextAnnotation>();
		for (TextAnnotation annot : annotations) {
			String coveredText = SpanUtils.getCoveredText(annot.getSpans(), documentText);
			if (coveredText.length() != coveredText.trim().length()) {
				if (!coveredText.trim().isEmpty()) {
					updatedAnnotations.add(CoNLLCoref2012DocumentReader.trimAnnotation(documentText, annot));
				}
			} else {
				updatedAnnotations.add(annot);
			}
		}
		return updatedAnnotations;
	}

	/**
	 * There was one instance of an annotation of type "knowtator support class" that was changed to
	 * "Noun Phrase"
	 * 
	 * @param annotations
	 * @param npAnnotations
	 * @return
	 */
	private static int removeKnowtatorSupportClass(Set<TextAnnotation> annotations, Set<TextAnnotation> npAnnotations) {
		/*
		 * there is a single annotation of type "knowtator support class" that should instead be a
		 * Noun Phrase
		 */
		int knowtatorCount = 0;
		for (TextAnnotation ta : annotations) {
			if (ta.getClassMention().getMentionName().equals(KNOWTATOR_SUPPORT_CLASS)) {
				System.out.println("#### Changed annotation type: knowtator support class --> Noun Phrase");
				knowtatorCount++;
				System.out.print(CoNLLCoref2012DocumentWriter.toLogString(ta) + " ---> ");
				ta.getClassMention().setMentionName(CoNLLCoref2012DocumentReader.NOUN_PHRASE);
				System.out.println(CoNLLCoref2012DocumentWriter.toLogString(ta) + "\n");
				npAnnotations.add(ta);
			}
		}
		return knowtatorCount;
	}
}
