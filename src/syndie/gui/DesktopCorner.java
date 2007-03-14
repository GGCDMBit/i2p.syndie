package syndie.gui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import syndie.db.UI;

/**
 *
 */
class DesktopCorner {
    private UI _ui;
    private Composite _parent;
    private Composite _root;
    
    public DesktopCorner(Composite parent, UI ui) {
        _parent = parent;
        _ui = ui;
        initComponents();
    }
    
    protected void initComponents() {
        _root = new Composite(_parent, SWT.NONE);
        _root.setLayout(new FillLayout());
    }
    
    protected Composite getRoot() { return _root; }
    
    public void dispose() { getRoot().dispose(); }
}
