package edu.ucdenver.ccp.file.conversion;

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
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;

public class FileFormatConverter {

	private List<DocumentReader> docReaders;
	private DocumentWriter docWriter;

	/**
	 * @param docReaders
	 *            must be in the same order as the InputStream list specified in
	 *            the convert method
	 * @param docSerializer
	 */
	public FileFormatConverter(List<DocumentReader> docReaders, DocumentWriter docSerializer) {
		this.docReaders = docReaders;
		this.docWriter = docSerializer;
	}

	public void convert(String sourceId, String sourceDb, List<File> inputFiles, File outputFile, File documentTextFile,
			CharacterEncoding encoding) throws IOException {

		TextDocument masterDoc = null;
		for (int i = 0; i < docReaders.size(); i++) {
			TextDocument td = docReaders.get(i).readDocument(sourceId, sourceDb, inputFiles.get(i), documentTextFile,
					encoding);
			if (masterDoc == null) {
				masterDoc = td;
			} else {
				masterDoc.addAnnotations(td.getAnnotations());
			}
		}
		Collections.sort(masterDoc.getAnnotations(), TextAnnotation.BY_SPAN());
		docWriter.serialize(masterDoc, outputFile, encoding);
	}

	/**
	 * @param sourceId
	 * @param sourceDb
	 * @param inputStreams
	 *            must be in the same order as the DocumentReaders specified in
	 *            the constructor
	 * @param outputStream
	 * @param documentTextStream
	 * @param encoding
	 * @throws IOException
	 */
	public void convert(String sourceId, String sourceDb, List<InputStream> inputStreams, OutputStream outputStream,
			InputStream documentTextStream, CharacterEncoding encoding) throws IOException {
		TextDocument masterDoc = null;
		for (int i = 0; i < docReaders.size(); i++) {
			TextDocument td = docReaders.get(i).readDocument(sourceId, sourceDb, inputStreams.get(i),
					documentTextStream, encoding);
			if (masterDoc == null) {
				masterDoc = td;
			} else {
				masterDoc.addAnnotations(td.getAnnotations());
			}
		}
		Collections.sort(masterDoc.getAnnotations(), TextAnnotation.BY_SPAN());
		docWriter.serialize(masterDoc, outputStream, encoding);
	}

	public static void convert(List<InputFileFormat> sourceFormats, OutputFileFormat targetFormat, String sourceId,
			String sourceDb, List<File> inputFiles, File outputFile, File documentTextFile,
			CharacterEncoding encoding) {
		try {
			FileFormatConverter converter = FileFormatConverterFactory.getConverter(sourceFormats, targetFormat);
			converter.convert(sourceId, sourceDb, inputFiles, outputFile, documentTextFile, encoding);
		} catch (IOException e) {
			throw new IllegalStateException(
					"Error while performing file format conversion. Source format(s): " + sourceFormats.toString()
							+ " Target format: " + targetFormat.name() + " file: " + inputFiles.toString(),
					e);
		}
	}

}
