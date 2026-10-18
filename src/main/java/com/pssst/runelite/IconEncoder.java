package com.pssst.runelite;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;

/** Turns a RuneLite item image into the PNG the app stores. In memory only: nothing touches the disk. */
final class IconEncoder
{
	private IconEncoder()
	{
	}

	/** @return the icon, or null if there is no usable image */
	static StateDTO.IconEntry encode(String name, BufferedImage image)
	{
		if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0)
		{
			return null;
		}
		try
		{
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			if (!ImageIO.write(image, "png", out))
			{
				return null;
			}
			return new StateDTO.IconEntry(name == null ? "" : name, Base64.getEncoder().encodeToString(out.toByteArray()));
		}
		catch (IOException e)
		{
			return null;
		}
	}
}
