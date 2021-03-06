package edu.ucdenver.ccp.file.conversion.treebank;

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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.tools.ant.util.StringUtils;

import edu.emory.clir.clearnlp.bin.C2DConvert;
import edu.ucdenver.ccp.common.collections.CollectionsUtil;
import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.file.FileUtil;
import edu.ucdenver.ccp.common.file.FileWriterUtil;
import edu.ucdenver.ccp.common.file.reader.StreamLineIterator;
import edu.ucdenver.ccp.common.io.ClassPathUtil;

/**
 * Utility for converting treebank constituency parses to dependency parses. This utility use the
 * ClearNLP library to facilitate the conversion.
 */
public class TreebankToDependencyConverter {

	public static void convert(File treebankDirectory, File dependencyDirectory, HeadRule headRule) throws IOException {

		File headRuleFile = File.createTempFile("headrule_en", ".txt");
		ClassPathUtil.copyClasspathResourceToFile(headRule.path(), headRuleFile);

		for (Iterator<File> fileIter = FileUtil.getFileIterator(treebankDirectory, false); fileIter.hasNext();) {
			File treeFile = fileIter.next();
			String[] convertArgs = new String[] { "-h", headRuleFile.getAbsolutePath(), "-i",
					treeFile.getAbsolutePath(), "-l", "english" };
			C2DConvert.main(convertArgs);
			File outputFile = new File(treeFile.getAbsolutePath() + ".dep");
			File conllFile = repeatPOSColumn(outputFile);
			FileUtil.copy(conllFile, dependencyDirectory);
			FileUtil.deleteFile(conllFile);
			FileUtil.deleteFile(outputFile);
		}
	}

	/**
	 * @param outputFile
	 * @return the original conll file format has two columns for part of speech. One course-grained
	 *         and one fine-grained. If fine-grained is not available it is supposed to be equal to
	 *         the course-grained, so we repeat the POS column here.
	 * @throws IOException
	 */
	static File repeatPOSColumn(File depFile) throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		File conlluFile = new File(StringUtils.removeSuffix(depFile.getAbsolutePath(), "tree.dep") + "conll");

		try (BufferedWriter writer = FileWriterUtil.initBufferedWriter(conlluFile)) {
			for (StreamLineIterator lineIter = new StreamLineIterator(depFile, encoding); lineIter.hasNext();) {
				String line = lineIter.next().getText();

				if (line.trim().isEmpty()) {
					writer.write("\n");
				} else {
					List<String> tokens = new ArrayList<String>(Arrays.asList(line.split("\\t")));
					// replicate the POS for both course and fine-grained columns
					tokens.add(4, tokens.get(3));
					// remove final column (which is empty) to get down to 10 columns total
					tokens.remove(tokens.size() - 1);
					String updatedLine = CollectionsUtil.createDelimitedString(tokens, "\t");
					writer.write(updatedLine + "\n");
				}
			}
		}
		return conlluFile;
	}

}
