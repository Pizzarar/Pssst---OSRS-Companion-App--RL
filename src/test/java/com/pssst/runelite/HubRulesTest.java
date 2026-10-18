package com.pssst.runelite;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.client.plugins.PluginDescriptor;
import org.junit.Assert;
import org.junit.Test;

/** Plugin Hub rules that are easy to break by accident and cheap to scan for. See GUIDELINES.md. */
public class HubRulesTest
{
	private static final Pattern[] FORBIDDEN = {
		Pattern.compile("\\.interrupt\\("),
		Pattern.compile("Thread\\.sleep"),
		Pattern.compile("java\\.io\\.File\\b"),
		Pattern.compile("java\\.io\\.File(Reader|Writer|InputStream|OutputStream)\\b"),
		Pattern.compile("java\\.nio\\.file\\.(Files|Paths)\\b"),
		Pattern.compile("ProcessBuilder"),
		Pattern.compile("Runtime\\.getRuntime"),
		Pattern.compile("HttpURLConnection"),
		Pattern.compile("java\\.net\\.http"),
		Pattern.compile("new OkHttpClient"),
		Pattern.compile("new Gson\\(")
	};

	@Test
	public void noForbiddenApiAppearsInThePlugin() throws IOException
	{
		List<Path> sources;
		try (Stream<Path> walk = Files.walk(Paths.get("src/main/java")))
		{
			sources = walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
		}
		Assert.assertFalse(sources.isEmpty());
		for (Path source : sources)
		{
			String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
			for (Pattern pattern : FORBIDDEN)
			{
				Assert.assertFalse(source + " uses " + pattern, pattern.matcher(text).find());
			}
		}
	}

	@Test
	public void thePluginDirectoryNeedsAnInternalName()
	{
		PluginDescriptor descriptor = CompanionPlugin.class.getAnnotation(PluginDescriptor.class);
		Assert.assertEquals("pssst-companion", descriptor.internalName());
	}
}
