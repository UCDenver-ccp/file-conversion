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
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.ucdenver.ccp.common.collections.CollectionsUtil;
import edu.ucdenver.ccp.common.file.CharacterEncoding;

public class FileFormatConverterTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void testConvertBionlpToPubAnnotation() throws IOException {
		FileFormatConverter converter = FileFormatConverterFactory
				.getConverter(CollectionsUtil.createList(InputFileFormat.BIONLP), OutputFileFormat.PUBANNOTATION);

		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		/* fill text with enough characters so the spans below are valid */
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < 3200; i++) {
			sb.append("0123456789");
		}
		String documentText = sb.toString();
		String sourceId = "12345";
		String sourceDb = "PMC";
		String annotationStr = "T0\tCL_0000740 1861 1882\tretinal ganglion cell\n"
				+ "T1\tCL_0000235 5757 5768\tmacrophages\n"
				+ "T2\tCL_0000604 30862 30865;30875 30889\trod photoreceptors\n"
				+ "T3\tCL_0000573 30870 30889\tcone photoreceptors\n" + "T4\tCL_0000604 31099 31103\trods\n"
				+ "T5\tCL_0000573 31108 31113\tcones";

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		converter.convert(sourceId, sourceDb,
				CollectionsUtil.createList(new ByteArrayInputStream(annotationStr.getBytes())), outputStream,
				new ByteArrayInputStream(documentText.getBytes()), encoding);
		String json = outputStream.toString(encoding.getCharacterSetName()).replaceAll("\\s", "");

		String expectedJson = ("{" + "\"sourceid\":\"" + sourceId + "\"," + "\"sourcedb\":\"" + sourceDb + "\","
				+ "\"text\":\"" + documentText + "\"," + "\"denotations\":["
				+ "{\"id\":\"T1\",\"span\":{\"begin\":1861,\"end\":1882},\"obj\":\"CL_0000740\"},"
				+ "{\"id\":\"T2\",\"span\":{\"begin\":5757,\"end\":5768},\"obj\":\"CL_0000235\"},"
				+ "{\"id\":\"T3\",\"span\":{\"begin\":30862,\"end\":30865},\"obj\":\"_FRAGMENT\"},"
				+ "{\"id\":\"T4\",\"span\":{\"begin\":30875,\"end\":30889},\"obj\":\"CL_0000604\"},"
				+ "{\"id\":\"T5\",\"span\":{\"begin\":30870,\"end\":30889},\"obj\":\"CL_0000573\"},"
				+ "{\"id\":\"T6\",\"span\":{\"begin\":31099,\"end\":31103},\"obj\":\"CL_0000604\"},"
				+ "{\"id\":\"T7\",\"span\":{\"begin\":31108,\"end\":31113},\"obj\":\"CL_0000573\"}],"

				+ "\"relations\":[" + "{\"id\":\"R1\",\"subj\":\"T4\",\"pred\":\"_lexicallyChainedTo\",\"obj\":\"T3\"}"

				+ "]}").replaceAll("\\s", "");

		System.out.println("EXPECTED:" + expectedJson.substring(32000));
		System.out.println("GENERATD:" + json.substring(32000));

		assertEquals("PubAnnotation JSON not as expected.", expectedJson, json);

	}

	/**
	 * simulate reading from two annotation formats and combining them into a
	 * single format
	 * 
	 * @throws IOException
	 */
	@Test
	public void testConvertUsingMultipleReaders() throws IOException {
		FileFormatConverter converter = FileFormatConverterFactory.getConverter(
				CollectionsUtil.createList(InputFileFormat.BIONLP, InputFileFormat.PUBANNOTATION),
				OutputFileFormat.PUBANNOTATION);

		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		/* fill text with enough characters so the spans below are valid */
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < 3200; i++) {
			sb.append("0123456789");
		}
		String documentText = sb.toString();
		String sourceId = "12345";
		String sourceDb = "PMC";
		String bionlpData = "T0\tCL_0000740 1861 1882\tretinal ganglion cell\n"
				+ "T1\tCL_0000235 5757 5768\tmacrophages\n"
				+ "T2\tCL_0000604 30862 30865;30875 30889\trod photoreceptors\n"
				+ "T3\tCL_0000573 30870 30889\tcone photoreceptors\n" + "T4\tCL_0000604 31099 31103\trods\n"
				+ "T5\tCL_0000573 31108 31113\tcones";

		/* @formatter:off */
		String pubAnnotationData = "{\n"+
			   "\"text\": \"IRF-4 expression in CML may be induced by IFN-α therapy\",\n"+
			   "\"denotations\": [\n"+
			   "   {\"id\": \"T1\", \"span\": {\"begin\": 0, \"end\": 5}, \"obj\": \"Protein\"},\n"+
			   "   {\"id\": \"T2\", \"span\": {\"begin\": 42, \"end\": 47}, \"obj\": \"Promoter\"}\n"+
			   "],\n"+
			   "\"relations\": [\n"+
			   "   {\"id\": \"R1\", \"subj\": \"T2\", \"pred\": \"regulates\", \"obj\": \"T1\"}\n"+
			   "]\n"+
			"}";
		/* @formatter:on */

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		converter.convert(sourceId, sourceDb,
				CollectionsUtil.createList(new ByteArrayInputStream(bionlpData.getBytes()),
						new ByteArrayInputStream(pubAnnotationData.getBytes())),
				outputStream, new ByteArrayInputStream(documentText.getBytes()), encoding);
		String json = outputStream.toString(encoding.getCharacterSetName()).replaceAll("\\s", "");

		String expectedJson = ("{" + "\"sourceid\":\"" + sourceId + "\"," + "\"sourcedb\":\"" + sourceDb + "\","
				+ "\"text\":\"" + documentText + "\"," + "\"denotations\":["
				+ "{\"id\":\"T1\",\"span\":{\"begin\":0,\"end\":5},\"obj\":\"Protein\"},"
				+ "{\"id\":\"T2\",\"span\":{\"begin\":42,\"end\":47},\"obj\":\"Promoter\"},"
				+ "{\"id\":\"T3\",\"span\":{\"begin\":1861,\"end\":1882},\"obj\":\"CL_0000740\"},"
				+ "{\"id\":\"T4\",\"span\":{\"begin\":5757,\"end\":5768},\"obj\":\"CL_0000235\"},"
				+ "{\"id\":\"T5\",\"span\":{\"begin\":30862,\"end\":30865},\"obj\":\"_FRAGMENT\"},"
				+ "{\"id\":\"T6\",\"span\":{\"begin\":30875,\"end\":30889},\"obj\":\"CL_0000604\"},"
				+ "{\"id\":\"T7\",\"span\":{\"begin\":30870,\"end\":30889},\"obj\":\"CL_0000573\"},"
				+ "{\"id\":\"T8\",\"span\":{\"begin\":31099,\"end\":31103},\"obj\":\"CL_0000604\"},"
				+ "{\"id\":\"T9\",\"span\":{\"begin\":31108,\"end\":31113},\"obj\":\"CL_0000573\"}],"

				+ "\"relations\":[" + "{\"id\":\"R1\",\"subj\":\"T2\",\"pred\":\"regulates\",\"obj\":\"T1\"},"
				+ "{\"id\":\"R2\",\"subj\":\"T6\",\"pred\":\"_lexicallyChainedTo\",\"obj\":\"T5\"}"

				+ "]}").replaceAll("\\s", "");

		System.out.println("EXPECTED:" + expectedJson.substring(32000));
		System.out.println("GENERATD:" + json.substring(32000));

		assertEquals("PubAnnotation JSON not as expected.", expectedJson, json);

	}

}
