/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.update.search;

import java.net.*;
import java.util.*;

import org.eclipse.core.runtime.*;
import org.eclipse.update.core.*;
import org.eclipse.update.internal.search.*;

/**
 * This class is central to update search. The search pattern
 * is encapsulated in update search category, while the search
 * scope is defined in the scope object. When these two objects
 * are defined and set, search can be performed using the 
 * provided method. Search results are reported to the
 * result collector, while search progress is tracked using
 * the progress monitor.
 * <p>Classes that implement <samp>IUpdateSearchResultCollector</samp>
 * should call 'filter' to test if the match should be
 * accepted according to the filters added to the request.
 * 
 * @see UpdateSearchScope
 * @see IUpdateSearchCategory
 */
public class UpdateSearchRequest {
	private IUpdateSearchCategory category;
	private UpdateSearchScope scope;
	private boolean searchInProgress = false;
	private AggregateFilter aggregateFilter = new AggregateFilter();

	class AggregateFilter implements IUpdateSearchFilter {
		private ArrayList filters;
		public void addFilter(IUpdateSearchFilter filter) {
			if (filters == null)
				filters = new ArrayList();
			if (filters.contains(filter) == false)
				filters.add(filter);
		}
		
		public void removeFilter(IUpdateSearchFilter filter) {
			if (filters == null)
				return;
			filters.remove(filter);
		}
	
		public boolean accept(IFeature match) {
			if (filters == null)
				return true;
			for (int i = 0; i < filters.size(); i++) {
				IUpdateSearchFilter filter = (IUpdateSearchFilter) filters.get(i);
				if (filter.accept(match) == false)
					return false;
			}
			return true;
		}
		
		public boolean accept(IFeatureReference match) {
			if (filters == null)
				return true;
			for (int i = 0; i < filters.size(); i++) {
				IUpdateSearchFilter filter = (IUpdateSearchFilter) filters.get(i);
				if (filter.accept(match) == false)
					return false;
			}
			return true;
		}
	}

	/**
	 * The constructor that accepts the search category and 
	 * scope objects.
	 * @param category the actual search pattern that should be applied
	 * @param scope a list of sites that need to be scanned during the search
	 */
	public UpdateSearchRequest(
		IUpdateSearchCategory category,
		UpdateSearchScope scope) {
		this.category = category;
		this.scope = scope;
	}
	/**
	 * Adds a filter to this request. This method does nothing
	 * if search is alrady in progress. 
	 * @param filter the filter 
	 * @see UpdateSearchRequest#removeFilter
	 */
	public void addFilter(IUpdateSearchFilter filter) {
		if (searchInProgress)
			return;
		aggregateFilter.addFilter(filter);
	}
	/**
	 * Removes the filter from this request. This method does
	 * nothing if search is alrady in progress.
	 * @param filter the filter to remove
	 * @see UpdateSearchRequest#addFilter
	 */

	public void removeFilter(IUpdateSearchFilter filter) {
		if (searchInProgress)
			return;
		aggregateFilter.removeFilter(filter);
	}

	/**
	 * Sets the scope object. It is possible to reuse the search request
	 * object by modifying the scope and re-running the search.
	 * @param scope the new search scope
	 */
	public void setScope(UpdateSearchScope scope) {
		this.scope = scope;
	}
	/**
	 * Tests whether this search request is current running.
	 * @return <samp>true</samp> if the search is currently running, <samp>false</samp> otherwise.
	 */
	public boolean isSearchInProgress() {
		return searchInProgress;
	}

	/**
	 * Runs the search using the category and scope configured into
	 * this request. As results arrive, they are passed to the
	 * search result collector object.
	 * @param collector matched features are passed to this object
	 * @param monitor used to track the search progress
	 * @throws CoreException
	 */
	public void performSearch(
		IUpdateSearchResultCollector collector,
		IProgressMonitor monitor)
		throws CoreException {
		ArrayList statusList = new ArrayList();

		searchInProgress = true;
		IUpdateSearchQuery[] queries = category.getQueries();
		IUpdateSearchSite[] candidates = scope.getSearchSites();
		URL updateMapURL = scope.getUpdateMapURL();

		if (!monitor.isCanceled()) {
			int nsearchsites = 0;
			for (int i = 0; i < queries.length; i++) {
				if (queries[i].getQuerySearchSite() != null)
					nsearchsites++;
			}
			int ntasks = nsearchsites + queries.length * candidates.length;
			if (updateMapURL!=null) ntasks++;

			monitor.beginTask("Searching...", ntasks);

			try {
				UpdateMap updateMap=null;
				if (updateMapURL!=null) {
					updateMap = new UpdateMap();
					IStatus status =loadUpdateMap(updateMap, updateMapURL, new SubProgressMonitor(monitor, 1));
					if (status != null)
						statusList.add(status);
				}
				for (int i = 0; i < queries.length; i++) {
					IUpdateSearchQuery query = queries[i];
					IQueryUpdateSiteAdapter qsite = query.getQuerySearchSite();
					if (qsite != null) {
						// check for mapping
						IUpdateSiteAdapter mappedSite = getMappedSite(updateMap, qsite);
						SubProgressMonitor subMonitor =
							new SubProgressMonitor(monitor, 1);
						IStatus status =
							searchOneSite(
								mappedSite,
								null,
								query,
								collector,
								subMonitor);
						if (status != null)
							statusList.add(status);
						if (monitor.isCanceled())
							break;
					}
					for (int j = 0; j < candidates.length; j++) {
						if (monitor.isCanceled()) {
							break;
						}
						IUpdateSearchSite source = candidates[j];
						SubProgressMonitor subMonitor =
							new SubProgressMonitor(monitor, 1);
						IStatus status =
							searchOneSite(
								source,
								source.getCategoriesToSkip(),
								query,
								collector,
								subMonitor);
						if (status != null)
							statusList.add(status);
					}
					if (monitor.isCanceled())
						break;
				}
			} catch (CoreException e) {
				searchInProgress = false;
				monitor.done();
				throw e;
			}
		}
		searchInProgress = false;
		monitor.done();

		if (statusList.size() > 0) {
			IStatus[] children =
				(IStatus[]) statusList.toArray(new IStatus[statusList.size()]);
			MultiStatus multiStatus =
				new MultiStatus(
					"org.eclipse.update.core",
					ISite.SITE_ACCESS_EXCEPTION,
					children,
					"Search.networkProblems",
					null);
			throw new CoreException(multiStatus);
		}
	}

/*
 * Load the update map using the map URL found in the scope.
 */	
	private IStatus loadUpdateMap(UpdateMap map, URL url, IProgressMonitor monitor) throws CoreException {
		monitor.subTask("Loading update map from "+url.toString());
		try {
			map.load(url, monitor);
			monitor.worked(1);
		}
		catch (CoreException e) {
			IStatus status = e.getStatus();
			if (status == null
				|| status.getCode() != ISite.SITE_ACCESS_EXCEPTION)
				throw e;
			monitor.worked(1);
			return status;
		}
		return null;
	}
/*
 * See if this query site adapter is mapped in the map file
 * to a different URL.
 */
	private IUpdateSiteAdapter getMappedSite(UpdateMap map, IQueryUpdateSiteAdapter qsite) {
		if (map!=null && map.isLoaded()) {
			IUpdateSiteAdapter mappedSite = map.getMappedSite(qsite.getMappingId());
			if (mappedSite!=null) return mappedSite;
		}
		// no match - use original site if fallback allowed, or nothing.
		return map.isFallbackAllowed()? qsite : null;
	}

/*
 * Search one site using the provided query.
 */
	private IStatus searchOneSite(
		IUpdateSiteAdapter siteAdapter,
		String[] categoriesToSkip,
		IUpdateSearchQuery query,
		IUpdateSearchResultCollector collector,
		SubProgressMonitor monitor)
		throws CoreException {
		String text = "Contacting " + siteAdapter.getLabel() + "...";
		monitor.subTask(text);
		monitor.beginTask("", 10);
		URL siteURL = siteAdapter.getURL();

		ISite site;
		try {
			site =
				SiteManager.getSite(
					siteURL,
					new SubProgressMonitor(monitor, 1));
		} catch (CoreException e) {
			// Test the exception. If the exception is
			// due to the site connection problems,
			// allow the search to move on to 
			// the next site. Otherwise,
			// rethrow the exception, causing the search
			// to terminate.
			IStatus status = e.getStatus();
			if (status == null
				|| status.getCode() != ISite.SITE_ACCESS_EXCEPTION)
				throw e;
			monitor.worked(10);
			return status;
		}
		// If frozen connection was canceled, there will be no site.
		if (site == null) {
			monitor.worked(9);
			return null;
		}

		text = "Checking " + siteAdapter.getLabel() + "...";
		monitor.getWrappedProgressMonitor().subTask(text);

		query.run(
			site,
			categoriesToSkip,
			aggregateFilter,
			collector,
			new SubProgressMonitor(monitor, 9));
		return null;
	}
}