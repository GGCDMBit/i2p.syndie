package syndie.gui;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import syndie.data.SyndieURI;

public class SyndicationSchedulerTab extends BrowserTab {
    private SyndicationScheduler _scheduler;
    public SyndicationSchedulerTab(BrowserControl browser, SyndieURI uri) { super(browser, uri); }
    
    protected void initComponents() {
        getRoot().setLayout(new FillLayout());
        _scheduler = new SyndicationScheduler(getBrowser(), getRoot());
    }
    
    public boolean canShow(SyndieURI uri) {
        return super.canShow(uri) || getURI().getType().equals(uri.getType()) || uri.isArchive();
    }
    
    public void show(SyndieURI uri) { _scheduler.show(uri); }
    
    protected void disposeDetails() { _scheduler.dispose(); }
    
    public Image getIcon() { return ImageUtil.ICON_TAB_SYNDICATE; }
    public String getName() { return "Syndication"; }
    public String getDescription() { return "Schedule syndication events"; }
}
