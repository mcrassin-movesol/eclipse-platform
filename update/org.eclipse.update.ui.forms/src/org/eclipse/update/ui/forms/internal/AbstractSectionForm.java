package org.eclipse.update.ui.forms.internal;
/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
import java.util.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.*;

/**
 * This form class understands form sections.
 * It assumes that they are part of the form
 * and it offers life cycle handling of sections
 * once they are registered.
 */

public abstract class AbstractSectionForm extends AbstractForm {
	protected Vector sections = null;

	public void registerSection(FormSection section) {
		if (sections == null)
			sections = new Vector();
		if (!sections.contains(section))
			sections.add(section);
	}

	public void unregisterSection(FormSection section) {
		if (sections != null && sections.contains(section))
			sections.remove(section);
	}

	public void initialize(Object model) {
		if (sections != null) {
			for (Iterator iter = sections.iterator(); iter.hasNext();) {
				FormSection section = (FormSection) iter.next();
				section.initialize(model);
			}
		}
	}

	public void setFocus() {
		if (sections != null && sections.size() > 0) {
			FormSection firstSection = (FormSection) sections.firstElement();
			firstSection.setFocus();
		}
	}

	public void update() {
		if (sections != null) {
			for (Iterator iter = sections.iterator(); iter.hasNext();) {
				FormSection section = (FormSection) iter.next();
				section.update();
			}
		}
	}

	public void commitChanges(boolean onSave) {
		if (sections != null) {
			for (Iterator iter = sections.iterator(); iter.hasNext();) {
				FormSection section = (FormSection) iter.next();
				if (section.isDirty())
					section.commitChanges(onSave);
			}
		}
	}

	public boolean doGlobalAction(String actionId) {
		Control focusControl = getFocusControl();
		if (focusControl == null)
			return false;

		if (canPerformDirectly(actionId, focusControl))
			return true;
		Composite parent = focusControl.getParent();
		FormSection targetSection = null;
		while (parent != null) {
			Object data = parent.getData();
			if (data != null && data instanceof FormSection) {
				targetSection = (FormSection) data;
				break;
			}
			parent = parent.getParent();
		}
		if (targetSection != null) {
			return targetSection.doGlobalAction(actionId);
		}
		return false;
	}

	protected Control getFocusControl() {
		Control control = getControl();
		if (control == null || control.isDisposed())
			return null;
		Display display = control.getDisplay();
		Control focusControl = display.getFocusControl();
		if (focusControl == null || focusControl.isDisposed())
			return null;
		return focusControl;
	}

	public boolean canPaste(Clipboard clipboard) {
		Control focusControl = getFocusControl();
		if (focusControl == null)
			return false;
		Composite parent = focusControl.getParent();
		FormSection targetSection = null;
		while (parent != null) {
			Object data = parent.getData();
			if (data != null && data instanceof FormSection) {
				targetSection = (FormSection) data;
				break;
			}
			parent = parent.getParent();
		}
		if (targetSection != null) {
			return targetSection.canPaste(clipboard);
		}
		return false;
	}

	public void dispose() {
		if (sections != null) {
			for (Iterator iter = sections.iterator(); iter.hasNext();) {
				FormSection section = (FormSection) iter.next();
				section.dispose();
			}
		}
		super.dispose();
	}

	public static void ensureVisible(ScrolledComposite scomp, Control control) {
		Point controlSize = control.getSize();
		Rectangle area = scomp.getClientArea();
		Point areaOrigin = scomp.toDisplay(new Point(area.x, area.y));
		
		Point scompOrigin = scomp.toDisplay(scomp.getOrigin());
		Point controlOrigin = control.toDisplay(control.getLocation());
		
		int x = scompOrigin.x;
		int y = scompOrigin.y;

		if (controlOrigin.x < scompOrigin.x) {
			x = Math.max(areaOrigin.x, controlOrigin.x);
		}
		if (controlOrigin.y < scompOrigin.y) {
			y = Math.max(areaOrigin.y, controlOrigin.y);
		}
		if (controlOrigin.x + controlSize.x > scompOrigin.x + area.width) {
			x = Math.max(areaOrigin.x, controlOrigin.x + controlSize.x - area.width);
		}
		if (controlOrigin.y + controlSize.y > scompOrigin.y + area.height) {
			y = Math.max(areaOrigin.y, controlOrigin.y + controlSize.y - area.height);
		}
		scomp.setOrigin(scomp.toControl(new Point(x, y)));
	}
	
	public static void scrollVertical(ScrolledComposite scomp, boolean up) {
		scroll(scomp, 0, up?-30:30);
	}
	public static void scrollHorizontal(ScrolledComposite scomp, boolean left) {
		scroll(scomp, left?-30:30, 0);
	}
	public static void scrollPage(ScrolledComposite scomp, boolean up) {
		Point origin = scomp.getOrigin();
		Rectangle clientArea = scomp.getClientArea();
		int increment = up ?  -clientArea.height : clientArea.height;
		scroll(scomp, origin.x, origin.y + increment);
	}
	private static void scroll(ScrolledComposite scomp, int xoffset, int yoffset) {
		Point origin = scomp.getOrigin();
		Point contentSize = scomp.getContent().getSize();
		int xorigin = origin.x + xoffset;
		int yorigin = origin.y + yoffset;
		xorigin = Math.max(xorigin, 0);
		xorigin = Math.min(xorigin, contentSize.x-1);
		yorigin = Math.max(yorigin, 0);
		yorigin = Math.min(yorigin, contentSize.y-1);
		scomp.setOrigin(xorigin, yorigin);
	}
}