/*******************************************************************************
    This file is part of miRTrace.

    COPYRIGHT: Marc Friedländer <marc.friedlander@scilifelab.se>, 2018
    AUTHOR: Yrin Eldfjell <yete@kth.se>

    miRTrace is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, version 3 of the License.

    miRTrace is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program, see the LICENSES file.
    If not, see <https://www.gnu.org/licenses/>.
*******************************************************************************/
package se.scilifelab.mirtrace;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;

class ReportBuilderHTML {
	
	Config pconf;
	
	ReportBuilderHTML(Config pconf) {
		this.pconf = pconf;
	}
	
	void fail(String msg) {
		throw new RuntimeException(msg);
	}
	
	String inputStreamToString(InputStream is) {
		int BUF_SIZE = 8192;
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		byte[] buffer = new byte[BUF_SIZE];
		int n = 0;
		while (true) {
			try {
				n = is.read(buffer);
			} catch (IOException e) {
				fail("Internal error while reading resource.");
			}
			if (n == -1) {
				break;
			}
			result.write(buffer, 0, n);
		}
		try {
			return result.toString("UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			fail("Internal error while reading resource.");
		}
		return ""; // Unreachable code. Has to be here to prevent complaints.
	}
	
	String buildHTMLFile(String htmlTemplate, String d3JS, String appJS, String jsonText, 
			String colorBrewerJS, String fileSaverJS) {
		htmlTemplate = htmlTemplate.replaceFirst("!MIRTRACE!JSON_STATS_BLOCK!", 
				Matcher.quoteReplacement(jsonText));
		htmlTemplate = htmlTemplate.replaceFirst("!MIRTRACE!d3[.]min[.]js!", 
				Matcher.quoteReplacement(d3JS));
		htmlTemplate = htmlTemplate.replaceFirst("!MIRTRACE!app[.]js!", 
				Matcher.quoteReplacement(appJS));
		htmlTemplate = htmlTemplate.replaceFirst("!MIRTRACE!colorbrewer[.]js!", 
				Matcher.quoteReplacement(colorBrewerJS));
		htmlTemplate = htmlTemplate.replaceFirst("!MIRTRACE!filesaver[.]js!", 
				Matcher.quoteReplacement(fileSaverJS));
		return htmlTemplate;
	}
	
	void generateReport(String jsonText) {
		try {
			String htmlTemplate = inputStreamToString(this.getClass().getClassLoader().getResourceAsStream(
					"templates/app.html"));
			String d3JS = inputStreamToString(this.getClass().getClassLoader().getResourceAsStream(
					"templates/d3.min.js"));
			
			/* The minified version of app.js is currently broken. It gives this error:
			 * "TypeError: e is undefined". 
			 * So we're using the non-minified version until further notice. */
			String appJS = inputStreamToString(this.getClass().getClassLoader().getResourceAsStream(
					"templates/app.js"));
			String colorBrewerJS = inputStreamToString(this.getClass().getClassLoader().getResourceAsStream(
					"templates/colorbrewer.min.js"));
			String fileSaverJS = inputStreamToString(this.getClass().getClassLoader().getResourceAsStream(
					"templates/filesaver.min.js"));
			String htmlOutput = buildHTMLFile(htmlTemplate, d3JS, appJS, jsonText, colorBrewerJS, fileSaverJS);
			String htmlFilename = new File(pconf.getMainOutputDirectory(), 
					Config.HTML_FILENAME).getAbsolutePath();
			PrintWriter htmlOutFile = new PrintWriter(htmlFilename, "UTF-8");
			htmlOutFile.write(htmlOutput);
			htmlOutFile.close();			
		} catch (IOException e) {
			e.printStackTrace();
			fail("Could not generate HTML file. Do you have write permissions?");
		}
		
	}
}
