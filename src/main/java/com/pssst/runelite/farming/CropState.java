package com.pssst.runelite.farming;

/** Mirrors RuneLite's timetracking CropState names; the strings go over the wire as-is. */
public enum CropState
{
	HARVESTABLE,
	GROWING,
	DISEASED,
	DEAD,
	EMPTY,
	FILLING
}
