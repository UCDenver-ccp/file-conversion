package edu.ucdenver.ccp.file.conversion.conllcoref2012;

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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.file.reader.Line;
import edu.ucdenver.ccp.datasource.fileparsers.SingleLineFileRecordReader;

/**
 * A description of the CoNLL Coreference 2011/12 file format is available here:
 * http://conll.cemantix.org/2012/data.html (scroll to "*_conll File Format"
 * heading near bottom of page)
 */
public class CoNLLCoref2012RecordReader extends SingleLineFileRecordReader<CoNLLCoref2012FileRecord> {

	private static final String SKIP_LINE_PREFIX = "#";

	public CoNLLCoref2012RecordReader(File dataFile, CharacterEncoding encoding) throws IOException {
		super(dataFile, encoding, SKIP_LINE_PREFIX);
	}

	public CoNLLCoref2012RecordReader(InputStream stream, CharacterEncoding encoding) throws IOException {
		super(stream, encoding, SKIP_LINE_PREFIX);
	}

	@Override
	protected CoNLLCoref2012FileRecord parseRecordFromLine(Line line) {
		int index = 0;
		String[] columns = line.getText().split("\\t");

		/*
		 * capture blank line with a blank doc id and null values for the rest
		 * of the fields
		 */
		if (columns.length == 1) {
			return new CoNLLCoref2012FileRecord("", null, -1, null, null, null, null, null, null, null, null, null,
					null, line.getByteOffset(), line.getLineNumber());
		}

		String documentId = columns[index++];
		String partNumber = columns[index++];
		int workNumber = Integer.parseInt(columns[index++]);
		String word = columns[index++];
		String partOfSpeech = columns[index++];
		String parseBit = columns[index++];
		String predicateLemma = columns[index++];
		String predicateFramesetId = columns[index++];
		String wordSense = columns[index++];
		String speakerAuthor = columns[index++];
		String namedEntities = columns[index++];

		List<String> predicateArguments = new ArrayList<String>();
		for (int i = index; i < columns.length - 2; i++) {
			predicateArguments.add(columns[i]);
		}
		String coreference = columns[columns.length - 1];

		return new CoNLLCoref2012FileRecord(documentId, partNumber, workNumber, word, partOfSpeech, parseBit,
				predicateLemma, predicateFramesetId, wordSense, speakerAuthor, namedEntities, predicateArguments,
				coreference, line.getByteOffset(), line.getLineNumber());
	}

}
