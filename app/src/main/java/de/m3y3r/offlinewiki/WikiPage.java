/*
 * Copyright 2012 Thomas Meyer
 */

package de.m3y3r.offlinewiki;

import java.util.Date;
import java.util.List;

public class WikiPage implements Comparable<WikiPage> {

	private final String title;
	private final long id;
//	private final WikiRevision revision;
	private final long revId;
	private final Date timestamp;
	private final List<String> contributorIp;
	private final String comment;
	private final String text;

	public static class Builder {
		private String title;
		private long id;
//		private final WikiRevision revision;
		private long revId;
		private Date timestamp;
		private List<String> contributorIp;
		private String comment;
		private String text;

		public void setTitle(String title) {
			this.title = title;
		}

		public void setId(long id) {
			this.id = id;
		}

		public void setRevId(long revId) {
			this.revId = revId;
		}

		public void setTimestamp(Date timestamp) {
			this.timestamp = timestamp;
		}

		public void setContributorIp(List<String> contributorIp) {
			this.contributorIp = contributorIp;
		}

		public void setComment(String comment) {
			this.comment = comment;
		}

		public void setText(String text) {
			this.text = text;
		}

		public void setRevisionId(long parseLong) {
			revId = parseLong;
		}

		WikiPage build() {
			if(title == null)
				throw new RuntimeException("Title is not set!");

			return new WikiPage(this);
		}
	}

	private WikiPage(Builder builder) {
		this.title = builder.title;
		this.id = builder.id;
		this.revId = builder.revId;
		this.timestamp = builder.timestamp;
		this.contributorIp = builder.contributorIp;
		this.comment = builder.comment;
		this.text = builder.text;
	}

	public String getTitle() {
		return title;
	}

	public long getId() {
		return id;
	}

	public long getRevId() {
		return revId;
	}

	public Date getTimestamp() {
		return timestamp;
	}

	public List<String> getContributorIp() {
		return contributorIp;
	}

	public String getComment() {
		return comment;
	}

	public String getText() {
		return text;
	}

	@Override
	public int compareTo(WikiPage o) {
		return title.compareTo(o.title);
	}

	@Override
	public String toString() {
		return "[" + title + "-" + id + "-" +revId + "]";
	}

}
