package edu.ucdenver.ccp.file.conversion.pubannotation;

/*-
 * #%L
 * Colorado Computational Pharmacology's file conversion
 * 						project
 * %%
 * Copyright (C) 2019 Regents of the University of Colorado
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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.file.FileWriterUtil;
import edu.ucdenver.ccp.file.conversion.DocumentWriter;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.mention.ClassMention;
import edu.ucdenver.ccp.nlp.core.mention.ComplexSlotMention;
import lombok.Data;

/**
 * Currently does not handle modifications or annotation tracks. For details,
 * see http://www.pubannotation.org/docs/annotation-format/
 */
public class PubAnnotationDocumentWriter extends DocumentWriter {

	public static final String FRAGMENT_INDICATOR = "_FRAGMENT";
	public static final String LEXICALLY_CHAINED_PREDICATE = "_lexicallyChainedTo";

	@Override
	public void serialize(TextDocument td, OutputStream outputStream, CharacterEncoding encoding) throws IOException {
		Document d = new Document(td.getSourceid(), td.getSourcedb(), td.getText());

		try {
			// create all denotations
			Map<String, Denotation> annotKeyToDenotationMap = new HashMap<String, Denotation>();
			for (TextAnnotation annot : td.getAnnotations()) {
				Denotation subjDenotation = addDenotation(d, annotKeyToDenotationMap, annot);
				// add a Relation to the document for each ComplexSlotMention
				for (ComplexSlotMention csm : annot.getClassMention().getComplexSlotMentions()) {
					addRelation(d, annotKeyToDenotationMap, subjDenotation, csm);
				}
			}

			Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
			String json = gson.toJson(d);

			try (BufferedWriter writer = FileWriterUtil.initBufferedWriter(outputStream, encoding)) {
				writer.write(json);
			}

		} catch (CloneNotSupportedException e) {
			throw new IOException("Error while generating PubAnnotation JSON.", e);
		}
	}

	public void addRelation(Document d, Map<String, Denotation> annotKeyToDenotationMap, Denotation subjDenotation,
			ComplexSlotMention csm) throws CloneNotSupportedException {
		String relationType = csm.getMentionName();
		for (ClassMention cm : csm.getClassMentions()) {
			TextAnnotation relationObjectAnnot = cm.getTextAnnotation();
			Denotation objDenotation = addDenotation(d, annotKeyToDenotationMap, relationObjectAnnot);
			Relation r = new Relation(subjDenotation.getId(), relationType, objDenotation.getId());
			d.addRelation(r);
		}
	}

	public Denotation addDenotation(Document d, Map<String, Denotation> annotKeyToDenotationMap, TextAnnotation annot)
			throws CloneNotSupportedException {
		String annotKey = annot.getSpans().toString() + annot.getClassMention().getMentionName();
		Denotation denotationToReturn = null;
		if (!annotKeyToDenotationMap.containsKey(annotKey)) {
			Denotation denotation = null;
			for (int i = 0; i < annot.getSpans().size(); i++) {
				edu.ucdenver.ccp.nlp.core.annotation.Span span = annot.getSpans().get(i);
				String type = (i < annot.getSpans().size() - 1) ? FRAGMENT_INDICATOR
						: annot.getClassMention().getMentionName();
				Denotation denot = new Denotation(span.getSpanStart(), span.getSpanEnd(), type);
				Denotation docDenotation = d.addDenotation(denot);

				/*
				 * if discontinuous span, add the _lexicallyChainedTo relation
				 */

				if (i > 0) {
					Relation r = new Relation(docDenotation.getId(), LEXICALLY_CHAINED_PREDICATE, denotation.getId());
					d.addRelation(r);
				}

				denotation = docDenotation;
			}
			annotKeyToDenotationMap.put(annotKey, denotation);
			denotationToReturn = denotation;
		} else {
			denotationToReturn = annotKeyToDenotationMap.get(annotKey);
		}
		return denotationToReturn;
	}

	@Data
	static class Denotation {
		@Expose
		private String id;
		@Expose
		private final Span span;
		@Expose
		private final String obj;

		public Denotation(int begin, int end, String obj) {
			this.span = new Span(begin, end);
			this.obj = obj;
		}

		/* equals() and hashcode() ignore the id field */
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Denotation other = (Denotation) obj;
			if (this.obj == null) {
				if (other.obj != null)
					return false;
			} else if (!this.obj.equals(other.obj))
				return false;
			if (span == null) {
				if (other.span != null)
					return false;
			} else if (!span.equals(other.span))
				return false;
			return true;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((obj == null) ? 0 : obj.hashCode());
			result = prime * result + ((span == null) ? 0 : span.hashCode());
			return result;
		}

		@Override
		protected Denotation clone() throws CloneNotSupportedException {
			return new Denotation(this.getSpan().getBegin(), this.getSpan().getEnd(), this.obj);
		}

	}

	@Data
	static class Span {
		@Expose
		private final int begin;
		@Expose
		private final int end;
	}

	@Data
	static class Relation {
		@Expose
		private String id;
		@Expose
		private final String subj;
		@Expose
		private final String pred;
		@Expose
		private final String obj;

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Relation other = (Relation) obj;
			if (this.obj == null) {
				if (other.obj != null)
					return false;
			} else if (!this.obj.equals(other.obj))
				return false;
			if (pred == null) {
				if (other.pred != null)
					return false;
			} else if (!pred.equals(other.pred))
				return false;
			if (subj == null) {
				if (other.subj != null)
					return false;
			} else if (!subj.equals(other.subj))
				return false;
			return true;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((obj == null) ? 0 : obj.hashCode());
			result = prime * result + ((pred == null) ? 0 : pred.hashCode());
			result = prime * result + ((subj == null) ? 0 : subj.hashCode());
			return result;
		}

		@Override
		protected Relation clone() throws CloneNotSupportedException {
			return new Relation(this.getSubj(), this.getPred(), this.getObj());
		}

	}

	@Data
	static class Document {
		@Expose
		private final String sourceid;
		@Expose
		private final String sourcedb;
		@Expose
		private final String text;

		/*
		 * Fields lacking @Expose annotation will not be serialized to JSON
		 */

		private int denotationIndex = 1;
		private int relationIndex = 1;

		private Map<Denotation, String> denotToIdMap;
		@Expose
		private List<Denotation> denotations;

		private Map<Relation, String> relationToIdMap;
		@Expose
		private List<Relation> relations;

		public Denotation addDenotation(Denotation d) throws CloneNotSupportedException {
			if (denotations == null) {
				denotations = new ArrayList<Denotation>();
				denotToIdMap = new HashMap<Denotation, String>();
			}
			Denotation denot = d.clone();
			if (denotToIdMap.containsKey(d)) {
				String denotId = denotToIdMap.get(d);
				denot.setId(denotId);
			} else {
				String denotId = "T" + denotationIndex++;
				denot.setId(denotId);
				denotToIdMap.put(denot, denotId);
				denotations.add(denot);
			}
			return denot;
		}

		public Relation addRelation(Relation r) throws CloneNotSupportedException {
			if (relations == null) {
				relations = new ArrayList<Relation>();
				relationToIdMap = new HashMap<Relation, String>();
			}
			Relation rel = r.clone();
			if (relationToIdMap.containsKey(rel)) {
				String relId = relationToIdMap.get(rel);
				rel.setId(relId);
			} else {
				String relId = "R" + relationIndex++;
				rel.setId(relId);
				relationToIdMap.put(rel, relId);
				relations.add(rel);
			}
			return rel;
		}

		public boolean containsDenotation(Denotation d) {
			return denotToIdMap.containsKey(d);
		}

		public boolean containsRelation(Relation r) {
			return relationToIdMap.containsKey(r);
		}
	}

}
