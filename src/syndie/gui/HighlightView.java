package syndie.gui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.i2p.data.DataHelper;
import net.i2p.data.Hash;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ArmEvent;
import org.eclipse.swt.events.ArmListener;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import syndie.Constants;
import syndie.data.ChannelInfo;
import syndie.data.NymReferenceNode;
import syndie.data.ReferenceNode;
import syndie.data.SyndieURI;
import syndie.db.ArchiveIndex;
import syndie.db.SyndicationManager;

/**
 *
 */
public class HighlightView implements Themeable, Translatable, SyndicationManager.SyndicationListener {
    private Composite _parent;
    private BrowserControl _browser;
    private Composite _root;
    private Tree _tree;
    private Button _browseForums;
    private Button _post;
    private Button _createForum;
    
    private TreeColumn _colSummary;
    private TreeColumn _colDetail;
    
    private TreeItem _itemPrivateMessages;
    private TreeItem _itemWatchedForums;
    private TreeItem _itemArchives;
    private TreeItem _itemNewForums;
    private TreeItem _itemPostponed;
    
    /** ordered list of forums (Hash) underneath the _itemWatchedForums */
    private List _watchedForums;
    /** ordered list of forums (Hash) underneath the _itemNewForums */
    private List _newForums;
    
    public HighlightView(BrowserControl browser, Composite parent) {
        _browser = browser;
        _parent = parent;
        _watchedForums = new ArrayList();
        _newForums = new ArrayList();
        initComponents();
        refreshHighlights();
        _browser.getSyndicationManager().addListener(this);
        browser.getSyndicationManager().loadArchives();
    }
    
    public void refreshHighlights() {
        _tree.setRedraw(false);
        updatePrivateMessages();
        updateWatchedForums();
        updateArchives();
        updateNewForums();
        updatePostponed();
        _tree.setRedraw(true);
    }
    
    private void updatePrivateMessages() {
        _itemPrivateMessages.setText(1, 0+"");
        rethemePrivateMessages(_browser.getThemeRegistry().getTheme());
    }
    private void updateWatchedForums() {
        List refs = _browser.getBookmarks();
        Set watched = getWatched(refs);
        Map watchedNames = getNames(watched);
        List sortedNames = sortWatchedForums(watchedNames);
        _browser.getUI().debugMessage("sorted names: " + sortedNames + " out of " + watchedNames.values());
        TreeItem items[] = _itemWatchedForums.getItems();
        if (items != null) for (int i = 0; i < items.length; i++) items[i].dispose();
        int totalUnread = 0;
        int activeForums = 0;
        int scopeIndex = 0;
        for (int i = 0; i < sortedNames.size(); i++, scopeIndex++) {
            Hash scope = (Hash)_watchedForums.get(scopeIndex);
            String name = (String)sortedNames.get(i);
            int unread = _browser.getClient().countUnreadMessages(scope);
            totalUnread += unread;
            if (unread > 0)
                activeForums++;
            _browser.getUI().debugMessage("# unread messages for [" + name + "] / [" + scope.toBase64().substring(0,6) + "]: " + unread);
            if (unread > 0) {
                TreeItem item = new TreeItem(_itemWatchedForums, SWT.NONE);
                item.setText(0, name);
                item.setText(1, _browser.getTranslationRegistry().getText(T_WATCHED_DETAIL_PREFIX, "Unread: ") + unread);
                setMinWidth(_colSummary, name, 50);
            } else {
                _watchedForums.remove(scopeIndex);
                scopeIndex--;
            }
        }
        _itemWatchedForums.setText(1, _browser.getTranslationRegistry().getText(T_WATCHED_DETAIL_SUMMARY_PREFIX, "Active forums/unread messages: ") + activeForums + "/" + totalUnread);
        rethemeWatchedForums(_browser.getThemeRegistry().getTheme());
    }
    private List sortWatchedForums(Map scopeToName) {
        _watchedForums.clear();
        List names = new ArrayList();
        TreeSet sortedNames = new TreeSet(scopeToName.values());
        for (Iterator iter = sortedNames.iterator(); iter.hasNext(); ) {
            String name = (String)iter.next();
            List hashes = getSortedForums(name, scopeToName);
            // qualify dups
            int sz = hashes.size();
            if (sz != 1) {
                for (int i = 0; i < sz; i++) {
                    Hash forum = (Hash)hashes.get(i);
                    _watchedForums.add(forum);
                    names.add(name + " - " + forum.toBase64().substring(0,6));
                }
            } else {
                Hash forum = (Hash)hashes.get(0);
                _watchedForums.add(forum);
                names.add(name);
            }
        }
        return names;
    }
    private List getSortedForums(String name, Map scopeToName) {
        List matches = new ArrayList();
        for (Iterator iter = scopeToName.entrySet().iterator(); iter.hasNext(); ) {
            Map.Entry entry = (Map.Entry)iter.next();
            Hash scope = (Hash)entry.getKey();
            String curName = (String)entry.getValue();
            if (name.equals(curName))
                matches.add(scope);
        }
        if (matches.size() > 1)
            Collections.sort(matches); // Hash is comparable
        _browser.getUI().debugMessage("sorted forums named [" + name + "]: " + matches);
        return matches;
    }
    
    private Set getWatched(List refs) {
        Set rv = new HashSet();
        if (refs != null) {
            for (int i = 0; i < refs.size(); i++) {
                ReferenceNode node = (ReferenceNode)refs.get(i);
                getWatched(node, rv);
            }
        }
        return rv;
    }
    private void getWatched(ReferenceNode node, Set rv) {
        SyndieURI uri = node.getURI();
        if (uri == null) return;
        Hash scope = null;
        if (uri.isChannel() && uri.getMessageId() == null)
            scope = uri.getScope();
        else if (uri.isSearch())
            scope = uri.getSearchScope();
        if (scope != null)
            rv.add(scope);
        for (int i = 0; i < node.getChildCount(); i++)
            getWatched(node.getChild(i), rv);
    }
    private Map getNames(Set forums) {
        Map rv = new HashMap();
        for (Iterator iter = forums.iterator(); iter.hasNext(); ) {
            Hash forum = (Hash)iter.next();
            String name = _browser.getClient().getChannelName(forum);
            if (name == null)
                name = forum.toBase64().substring(0, 6);
            rv.put(forum, name);
        }
        return rv;
    }
    
    private void updateArchives() {
        TreeItem items[] = _itemArchives.getItems();
        if (items != null) for (int i = 0; i < items.length; i++) items[i].dispose();
        
        SyndicationManager mgr = _browser.getSyndicationManager();
        int archives = mgr.getArchiveCount();
        _browser.getUI().debugMessage("known archives: " + archives);
        int recentlySynced = 0;
        for (int i = 0; i < archives; i++) {
            String name = mgr.getArchiveName(i);
            ArchiveIndex index = mgr.getArchiveIndex(i);
            long syncAge = -1;
            long builtOn = -1;
            if (index != null) {
                builtOn = index.getBuiltOn();
                syncAge = System.currentTimeMillis() - builtOn;
                if (syncAge < 3*60*60*1000L)
                    recentlySynced++;
            }
            TreeItem item = new TreeItem(_itemArchives, SWT.NONE);
            item.setText(0, name);
            if (syncAge > 0)
                item.setText(1, _browser.getTranslationRegistry().getText(T_ARCHIVE_DETAIL_PREFIX, "Last sync: ") + getDateTime(builtOn));
            else
                item.setText(1, _browser.getTranslationRegistry().getText(T_ARCHIVE_DETAIL_NEVERSYNCED, "Last sync: never"));
        }
        _itemArchives.setText(1, _browser.getTranslationRegistry().getText(T_ARCHIVE_DETAIL_SUMMARY_PREFIX, "Total/pending sync") + ": " + archives + "/" + (archives-recentlySynced));
        rethemeArchives(_browser.getThemeRegistry().getTheme());
    }
    
    private void updateNewForums() {
        TreeItem items[] = _itemNewForums.getItems();
        if (items != null) for (int i = 0; i < items.length; i++) items[i].dispose();
        
        // list of forums where the nym hasn't set a nymChannelReadThrough date (not even one in 1970)
        _newForums.clear();
        int activeForums = 0;
        int newMessages = 0;
        List channelIds = _browser.getClient().getNewChannelIds();
        for (int i = 0; i < channelIds.size(); i++) {
            Long channelId = (Long)channelIds.get(i);
            ChannelInfo info = _browser.getClient().getChannel(channelId.longValue());
            int msgs = _browser.getClient().countUnreadMessages(info.getChannelHash());
            //if (msgs == 0)
            //    continue;
            _newForums.add(info.getChannelHash());
            TreeItem item = new TreeItem(_itemNewForums, SWT.NONE);
            String name = info.getName();
            if (name == null)
                item.setText(0, info.getChannelHash().toBase64().substring(0,6));
            else
                item.setText(0, name + " - " + info.getChannelHash().toBase64().substring(0,6));
            
            String desc = info.getDescription();
            if (msgs > 0) {
                activeForums++;
                newMessages += msgs;
            }
            
            if (desc == null)
                item.setText(1, _browser.getTranslationRegistry().getText(T_NEWFORUM_DETAIL_PREFIX, "Messages: ") + msgs);
            else
                item.setText(1, _browser.getTranslationRegistry().getText(T_NEWFORUM_DETAIL_PREFIX, "Messages: ") + msgs + " " + desc);
        }
        _itemNewForums.setText(1, _browser.getTranslationRegistry().getText(T_NEWFORUM_DETAIL_SUMMARY_PREFIX, "Total forums/active forums/messages") + ": " + channelIds.size() + "/" + activeForums + "/" + newMessages);        
        rethemeNewForums(_browser.getThemeRegistry().getTheme());
    }
    
    private void updatePostponed() {
        TreeItem items[] = _itemPostponed.getItems();
        if (items != null) for (int i = 0; i < items.length; i++) items[i].dispose();
        
        TreeMap resumeable = _browser.getResumeable();
        for (Iterator iter = resumeable.entrySet().iterator(); iter.hasNext(); ) {
            Map.Entry entry = (Map.Entry)iter.next();
            Long resumeableId = (Long)entry.getKey();
            Integer version = (Integer)entry.getValue();
            TreeItem item = new TreeItem(_itemPostponed, SWT.NONE);
            item.setText(0, _browser.getTranslationRegistry().getText(T_POSTPONED_NAME, "Postponed on"));
            item.setText(1, getDateTime(resumeableId.longValue()));
        }
        _itemPostponed.setText(1, Integer.toString(resumeable.size()));
        rethemePostponed(_browser.getThemeRegistry().getTheme());
    }
    
    private static final SimpleDateFormat _fmt = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
    private static final String getDateTime(long ts) {
        synchronized (_fmt) { return _fmt.format(new Date(ts)); }
    }
    
    private void initComponents() {
        _root = new Composite(_parent, SWT.NONE);
        _root.setLayout(new GridLayout(1, true));
        
        _tree = new Tree(_root, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION);
        _tree.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true));
        _tree.setHeaderVisible(true);
        _tree.setLinesVisible(true);
        
        final Menu menu = new Menu(_tree);
        _tree.setMenu(menu);
        menu.addMenuListener(new MenuListener() {
            public void menuHidden(MenuEvent menuEvent) {}
            public void menuShown(MenuEvent menuEvent) { reconfigMenu(menu); }
        });
        
        _colSummary = new TreeColumn(_tree, SWT.RIGHT);
        _colDetail = new TreeColumn(_tree, SWT.LEFT);
        
        SyndieTreeListener lsnr = new SyndieTreeListener(_tree);
        _tree.addControlListener(lsnr);
        _tree.addMouseListener(lsnr);
        _tree.addKeyListener(lsnr);
        _tree.addSelectionListener(lsnr);
        _tree.addTraverseListener(lsnr);
    
        _itemPrivateMessages = new TreeItem(_tree, SWT.NONE);
        _itemWatchedForums = new TreeItem(_tree, SWT.NONE);
        _itemArchives = new TreeItem(_tree, SWT.NONE);
        _itemNewForums = new TreeItem(_tree, SWT.NONE);
        _itemPostponed = new TreeItem(_tree, SWT.NONE);
        
        Composite actions = new Composite(_root, SWT.NONE);
        actions.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false));
        actions.setLayout(new FillLayout(SWT.HORIZONTAL));
        _browseForums = new Button(actions, SWT.PUSH);
        _post = new Button(actions, SWT.PUSH);
        _createForum = new Button(actions, SWT.PUSH);
        
        _browser.getTranslationRegistry().register(this);
        _browser.getThemeRegistry().register(this);
    }
    
    private void reconfigMenu(Menu menu) {
        MenuItem items[] = menu.getItems();
        for (int i = 0; i < items.length; i++) items[i].dispose();
        
        TreeItem sel[] = _tree.getSelection();
        if ( (sel != null) && (sel.length == 1) ) {
            TreeItem parent = sel[0].getParentItem();
            TreeItem child = null;
            if (parent == null)
                parent = sel[0];
            else
                child = sel[0];
            
            if (parent == _itemArchives) {
                reconfigArchiveMenu(menu, child);
            } else if (parent == _itemNewForums) {
                reconfigNewForumMenu(menu, child);
            } else if (parent == _itemPostponed) {
                reconfigPostponedMenu(menu, child);
            } else if (parent == _itemPrivateMessages) {
                reconfigPrivateMessagesMenu(menu, child);
            } else if (parent == _itemWatchedForums) {
                reconfigWatchedForumsMenu(menu, child);
            }
        }
    }
    private void reconfigArchiveMenu(Menu menu, TreeItem selected) {}
    private void reconfigNewForumMenu(Menu menu, final TreeItem selected) {
        if (selected != null) {
            TreeItem parent = selected.getParentItem();
            final int idx = parent.indexOf(selected);
            if (idx == -1)
                return;
            
            final Hash forum = (Hash)_newForums.get(idx);
            
            MenuItem view = new MenuItem(menu, SWT.PUSH);
            view.setText(_browser.getTranslationRegistry().getText(T_NEWFORUM_MENU_VIEW, "View"));
            view.addSelectionListener(new SelectionListener() {
                public void widgetDefaultSelected(SelectionEvent selectionEvent) { _browser.view(SyndieURI.createScope(forum)); }
                public void widgetSelected(SelectionEvent selectionEvent) { _browser.view(SyndieURI.createScope(forum)); }
            });
            
            final long channelId = _browser.getClient().getChannelId(forum);
            MenuItem markRead = new MenuItem(menu, SWT.PUSH);
            markRead.setText(_browser.getTranslationRegistry().getText(T_NEWFORUM_MENU_MARKREAD, "Mark as read"));
            markRead.addSelectionListener(new SelectionListener() {
                public void widgetDefaultSelected(SelectionEvent selectionEvent) { 
                    _browser.getClient().markChannelRead(channelId); 
                    _tree.setRedraw(false);
                    updateNewForums();
                    _itemNewForums.setExpanded(true);
                    _tree.setRedraw(true);
                }
                public void widgetSelected(SelectionEvent selectionEvent) { 
                    _browser.getClient().markChannelRead(channelId);
                    _tree.setRedraw(false);
                    updateNewForums();
                    _itemNewForums.setExpanded(true);
                    _tree.setRedraw(true);
                }
            });
        }
    }
    private void reconfigPostponedMenu(Menu menu, TreeItem selected) {}
    private void reconfigPrivateMessagesMenu(Menu menu, TreeItem selected) {}
    private void reconfigWatchedForumsMenu(Menu menu, final TreeItem selected) {
        if (selected != null) {
            TreeItem parent = selected.getParentItem();
            final int idx = parent.indexOf(selected);
            if (idx == -1)
                return;
            
            final Hash forum = (Hash)_watchedForums.get(idx);
            
            MenuItem view = new MenuItem(menu, SWT.PUSH);
            view.setText(_browser.getTranslationRegistry().getText(T_WATCHED_MENU_VIEW, "View"));
            view.addSelectionListener(new SelectionListener() {
                public void widgetDefaultSelected(SelectionEvent selectionEvent) { _browser.view(SyndieURI.createScope(forum)); }
                public void widgetSelected(SelectionEvent selectionEvent) { _browser.view(SyndieURI.createScope(forum)); }
            });
            
            final long channelId = _browser.getClient().getChannelId(forum);
            MenuItem markRead = new MenuItem(menu, SWT.PUSH);
            markRead.setText(_browser.getTranslationRegistry().getText(T_WATCHED_MENU_MARKREAD, "Mark as read"));
            markRead.addSelectionListener(new SelectionListener() {
                public void widgetDefaultSelected(SelectionEvent selectionEvent) { 
                    _browser.getClient().markChannelRead(channelId); 
                    _tree.setRedraw(false);
                    updateWatchedForums();
                    _itemWatchedForums.setExpanded(true);
                    _tree.setRedraw(true);
                }
                public void widgetSelected(SelectionEvent selectionEvent) { 
                    _browser.getClient().markChannelRead(channelId);
                    _tree.setRedraw(false);
                    updateWatchedForums();
                    _itemWatchedForums.setExpanded(true);
                    _tree.setRedraw(true);
                }
            });
        }
    }
    
    public void applyTheme(Theme theme) {
        _tree.setFont(theme.TREE_FONT);
        _browseForums.setFont(theme.BUTTON_FONT);
        _post.setFont(theme.BUTTON_FONT);
        _createForum.setFont(theme.BUTTON_FONT);
        
        rethemePrivateMessages(theme);
        rethemeWatchedForums(theme);
        rethemeArchives(theme);
        rethemeNewForums(theme);
        rethemePostponed(theme);
        
        // font sizes may change, so expand as necessary
        setMinWidth(_colSummary, _itemPrivateMessages.getText(0), 50);
        setMinWidth(_colSummary, _itemWatchedForums.getText(0), 50);
        setMinWidth(_colSummary, _itemArchives.getText(0), 50);
        setMinWidth(_colSummary, _itemNewForums.getText(0), 50);
        setMinWidth(_colSummary, _itemPostponed.getText(0), 50);
    }
    
    private void rethemePrivateMessages(Theme theme) {
        if (_itemPrivateMessages.getItemCount() > 0) {
            _itemPrivateMessages.setFont(theme.HIGHLIGHT_ACTIVE_FONT);
            //TreeItem items[] = _itemPrivateMessages.getItems();
            //for (int i = 0; i < items.length; i++)
            //    items[i].setFont(theme.HIGHLIGHT_ACTIVE_FONT);
        } else {
            _itemPrivateMessages.setFont(theme.HIGHLIGHT_INACTIVE_FONT);
        }
    }
    private void rethemeWatchedForums(Theme theme) {
        if (_itemWatchedForums.getItemCount() > 0) {
            _itemWatchedForums.setFont(theme.HIGHLIGHT_ACTIVE_FONT);
            //TreeItem items[] = _itemWatchedForums.getItems();
            //for (int i = 0; i < items.length; i++)
            //    items[i].setFont(theme.HIGHLIGHT_ACTIVE_FONT);
        } else {
            _itemWatchedForums.setFont(theme.HIGHLIGHT_INACTIVE_FONT);
        }
    }
    private void rethemeArchives(Theme theme) {
        if (_itemArchives.getItemCount() > 0) {
            _itemArchives.setFont(theme.HIGHLIGHT_ACTIVE_FONT);
            //TreeItem items[] = _itemArchives.getItems();
            //for (int i = 0; i < items.length; i++)
            //    items[i].setFont(theme.HIGHLIGHT_ACTIVE_FONT);
        } else {
            _itemArchives.setFont(theme.HIGHLIGHT_INACTIVE_FONT);
        }
    }
    private void rethemeNewForums(Theme theme) {
        if (_itemNewForums.getItemCount() > 0) {
            _itemNewForums.setFont(theme.HIGHLIGHT_ACTIVE_FONT);
            //TreeItem items[] = _itemNewForums.getItems();
            //for (int i = 0; i < items.length; i++)
            //    items[i].setFont(theme.HIGHLIGHT_ACTIVE_FONT);
        } else {
            _itemNewForums.setFont(theme.HIGHLIGHT_INACTIVE_FONT);
        }
    }
    private void rethemePostponed(Theme theme) {
        if (_itemPostponed.getItemCount() > 0) {
            _itemPostponed.setFont(theme.HIGHLIGHT_ACTIVE_FONT);
            //TreeItem items[] = _itemPostponed.getItems();
            //for (int i = 0; i < items.length; i++)
            //    items[i].setFont(theme.HIGHLIGHT_ACTIVE_FONT);
        } else {
            _itemPostponed.setFont(theme.HIGHLIGHT_INACTIVE_FONT);
        }
    }
    
    private static final String T_BROWSE = "syndie.gui.highlightview.browse";
    private static final String T_POST = "syndie.gui.highlightview.post";
    private static final String T_CREATEFORUM = "syndie.gui.highlightview.createforum";
    
    private static final String T_PRIVMSG = "syndie.gui.highlightview.privmsg";
    private static final String T_WATCHED = "syndie.gui.highlightview.watched";
    private static final String T_ARCHIVES = "syndie.gui.highlightview.archives";
    private static final String T_NEWFORUMS = "syndie.gui.highlightview.newforums";
    private static final String T_POSTPONED = "syndie.gui.highlightview.postponed";
    
    private static final String T_WATCHED_DETAIL_PREFIX = "syndie.gui.highlightview.watcheddetailprefix";
    private static final String T_WATCHED_DETAIL_SUMMARY_PREFIX = "syndie.gui.highlightview.watcheddetailsummaryprefix";
    
    private static final String T_ARCHIVE_DETAIL_PREFIX = "syndie.gui.highlightview.archivedetailprefix";
    private static final String T_ARCHIVE_DETAIL_NEVERSYNCED = "syndie.gui.highlightview.archivedetailneversynced";
    private static final String T_ARCHIVE_DETAIL_SUMMARY_PREFIX = "syndie.gui.highlightview.archivedetailsummaryprefix";
    private static final String T_NEWFORUM_DETAIL_PREFIX = "syndie.gui.highlightview.newforumdetailprefix";
    private static final String T_NEWFORUM_DETAIL_SUMMARY_PREFIX = "syndie.gui.highlightview.newforumdetailsummaryprefix";

    private static final String T_NEWFORUM_MENU_MARKREAD = "syndie.gui.highlightview.newforum.menu.markread";
    private static final String T_NEWFORUM_MENU_VIEW = "syndie.gui.highlightview.newforum.menu.view";

    private static final String T_WATCHED_MENU_MARKREAD = "syndie.gui.highlightview.watched.menu.markread";
    private static final String T_WATCHED_MENU_VIEW = "syndie.gui.highlightview.watched.menu.view";
    
    private static final String T_POSTPONED_NAME = "syndie.gui.highlightview.postponedname";
    
    public void translate(TranslationRegistry registry) {
        _browseForums.setText(registry.getText(T_BROWSE, "Browse forums"));
        _post.setText(registry.getText(T_POST, "Post a new message"));
        _createForum.setText(registry.getText(T_CREATEFORUM, "Create a new forum"));
        
        _itemPrivateMessages.setText(0, registry.getText(T_PRIVMSG, "Private messages"));
        _itemWatchedForums.setText(0, registry.getText(T_WATCHED, "Watched forums"));
        _itemArchives.setText(0, registry.getText(T_ARCHIVES, "Archives"));
        _itemNewForums.setText(0, registry.getText(T_NEWFORUMS, "New forums"));
        _itemPostponed.setText(0, registry.getText(T_POSTPONED, "Postponed messages"));

        _colSummary.pack();
        _colDetail.pack();
        
        setMinWidth(_colSummary, _itemPrivateMessages.getText(0), 50);
        setMinWidth(_colSummary, _itemWatchedForums.getText(0), 50);
        setMinWidth(_colSummary, _itemArchives.getText(0), 50);
        setMinWidth(_colSummary, _itemNewForums.getText(0), 50);
        setMinWidth(_colSummary, _itemPostponed.getText(0), 50);
    }

    private void setMinWidth(TreeColumn col, String text) { setMinWidth(col, text, 0); }
    private void setMinWidth(TreeColumn col, String text, int extraWidth) {
        int width = ImageUtil.getWidth(text, _tree) + _tree.getGridLineWidth()*2 + extraWidth;
        int existing = col.getWidth();
        if (width > existing) {
            _browser.getUI().debugMessage("Increasing the width on " + col.getText() + " from " + existing + " to " + width);
            col.setWidth(width);
        } else {
            _browser.getUI().debugMessage("Keeping the width on " + col.getText() + " at " + existing + " (new width would be " + width + ")");
        }
    }

    public void archivesLoaded(SyndicationManager mgr) { 
        _tree.getDisplay().asyncExec(new Runnable() { 
            public void run() { _tree.setRedraw(false); updateArchives(); _tree.setRedraw(true); }
        });
    }
    public void archiveAdded(SyndicationManager mgr, String name) { 
        _tree.getDisplay().asyncExec(new Runnable() { 
            public void run() { _tree.setRedraw(false); updateArchives(); _tree.setRedraw(true); }
        });
    }
    public void archiveRemoved(SyndicationManager mgr, String name) {
        _tree.getDisplay().asyncExec(new Runnable() { 
            public void run() { _tree.setRedraw(false); updateArchives(); _tree.setRedraw(true); }
        });
    }
    public void archiveUpdated(SyndicationManager mgr, String oldName, String newName) {
        _tree.getDisplay().asyncExec(new Runnable() { 
            public void run() { _tree.setRedraw(false); updateArchives(); _tree.setRedraw(true); }
        });
    }
    public void archiveIndexStatus(SyndicationManager mgr, SyndicationManager.StatusRecord record) {
        if (record.getStatus() == SyndicationManager.FETCH_INDEX_DIFF_OK) {
            _tree.getDisplay().asyncExec(new Runnable() { 
                public void run() { _tree.setRedraw(false); updateArchives(); _tree.setRedraw(true); }
            });
        }
    }
    public void fetchStatusUpdated(SyndicationManager mgr, SyndicationManager.StatusRecord record) {}
}
