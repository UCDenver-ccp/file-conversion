package edu.ucdenver.ccp.file.conversion.conllu;

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
import java.io.InputStream;

import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.file.reader.Line;
import edu.ucdenver.ccp.datasource.fileparsers.SingleLineFileRecordReader;

/**
 * A description of the CoNLL-U file format is available here:
 * https://universaldependencies.org/format.html
 */
public class CoNLLURecordReader extends SingleLineFileRecordReader<CoNLLUFileRecord> {

	private static final String SKIP_LINE_PREFIX = "#";

	public CoNLLURecordReader(File dataFile, CharacterEncoding encoding) throws IOException {
		super(dataFile, encoding, SKIP_LINE_PREFIX);
	}

	public CoNLLURecordReader(InputStream stream, CharacterEncoding encoding) throws IOException {
		super(stream, encoding, SKIP_LINE_PREFIX);
	}

	@Override
	protected CoNLLUFileRecord parseRecordFromLine(Line line) {
		int index = 0;
		String[] columns = line.getText().split("\\t");

		/*
		 * capture blank line (sentence boundary) with a -1 word index and null
		 * values for the rest of the fields
		 */
		if (columns.length == 1) {
			return new CoNLLUFileRecord(-1, null, null, null, null, null, -1, null, null, null, line.getByteOffset(),
					line.getLineNumber());
		}

		int wordIndex = Integer.parseInt(columns[index++]);
		String form = columns[index++];
		String lemma = columns[index++];
		String universalPartOfSpeechTag = columns[index++];
		String languageSpecificPartOfSpeechTag = columns[index++];
		String morphologicFeatures = columns[index++];
		String headStr = columns[index++];
		Integer head = (headStr.equals("_")) ? null : Integer.parseInt(headStr);
		String dependencyRelation = columns[index++];
		String enhancedDependencyGraph = columns[index++];
		String miscellaneous = (columns.length == index - 1) ? columns[index++] : null;

		return new CoNLLUFileRecord(wordIndex, form, lemma, universalPartOfSpeechTag, languageSpecificPartOfSpeechTag,
				morphologicFeatures, head, dependencyRelation, enhancedDependencyGraph, miscellaneous,
				line.getByteOffset(), line.getLineNumber());
	}

}
