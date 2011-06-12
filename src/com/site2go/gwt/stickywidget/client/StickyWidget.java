package com.site2go.gwt.stickywidget.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.query.client.GQuery;
import static com.google.gwt.query.client.GQuery.*;
import com.google.gwt.query.client.css.CSS;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.Window.ScrollEvent;
import com.google.gwt.user.client.Window.ScrollHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

public class StickyWidget 
	extends Composite
	implements ResizeHandler, ScrollHandler {
	private boolean detached;
	private boolean detachedTop;
	HandlerRegistration resizeReg, scrollReg;

	private Element el;
	private Style elStyle;
	private GQuery $el;
	
	DivElement ghostElement;

	// Cached for performance.
	int viewportWidth, viewportHeight;
	int scrollLeft, scrollTop;

	// The absolute top position of the Element before it was fixed to the document.
	int topAbsoluteOffset;

	// These are the values for a detached Widget element. Note these are only 
	// set if they existed in the style attribute of the Element.
	String previousPositioning, previousLeft, previousTop, previousBottom;
	String previousMarginTop, previousMarginRight, previousMarginBottom, previousMarginLeft;

	Timer attachmentCheckTimer;
	boolean attachmentCheckTimerScheduled;

	public StickyWidget(Widget w) {
		this.initWidget(w);
		
		this.attachmentCheckTimer = new Timer() {
			@Override
			public void run() {
				StickyWidget.this.attachmentCheckTimerScheduled = false;

				if(StickyWidget.this.detached) {
					StickyWidget.this.checkShouldReattach();
//					StickyWidget.this.checkShouldDetach();
				}
				else {
					StickyWidget.this.checkShouldDetach();
//					StickyWidget.this.checkShouldReattach();
				}
			}
		};
	}

	@Override
	protected void onLoad() {
		super.onLoad();

		this.el = this.getElement();
		this.$el = $(this.el);
		this.elStyle = this.el.getStyle();
		
		this.resizeReg = Window.addResizeHandler(this);
		this.scrollReg = Window.addWindowScrollHandler(this);

		this.viewportWidth = Window.getClientWidth();
		this.viewportHeight = Window.getClientHeight();
		this.scrollLeft = Window.getScrollLeft();
		this.scrollTop = Window.getScrollTop();

		GWT.log("Viewport: " + this.viewportWidth + "x" + this.viewportHeight);

		this.checkAttachment();
	}

	@Override
	protected void onUnload() {
		super.onUnload();

		this.resizeReg.removeHandler();
		this.scrollReg.removeHandler();
	}

	private void checkAttachment() {
		if(this.attachmentCheckTimerScheduled) {
			this.attachmentCheckTimer.cancel();
		}

		this.attachmentCheckTimerScheduled = true;
		this.attachmentCheckTimer.schedule(5);
	}

	/**
	 * Called when we're currently attached to the page as normal, and a page
	 * scroll/resize event has occurred. We check if we should be detaching from
	 * the page here.
	 */
	private void checkShouldDetach() {
		int elTop = this.el.getAbsoluteTop();

		// Time to detach?
		if(this.scrollTop >= elTop) {
			this.detach(true);
			return;
		}

		int elHeight = this.$el.outerHeight();
		if((this.scrollTop + this.viewportHeight) < (elTop + elHeight)) {
			this.detach(false);
		}
	}

	private void checkShouldReattach() {
		if(this.detachedTop) {
			if(this.scrollTop <= this.topAbsoluteOffset) {
				this.reattach();
				this.checkShouldDetach();
			}
		}
		else {
			int elHeight = this.$el.outerHeight();
			if((this.scrollTop + this.viewportHeight) >= (this.topAbsoluteOffset + elHeight)) {
				this.reattach();
				this.checkShouldDetach();
			}
		}
	}

	/**
	 * Detaching entails removing the Widget from standard page flow and fixing
	 * its position to the viewport. Of course we don't want to affect the current
	 * page flow at all, so we create a "ghost" Element that matches the Widget
	 * perfectly in dimensions and position.
	 */
	private void detach(boolean top) {
		// Before we do anything, we need to suss out some current details on the
		// current Element (positioning, dimensions, etc). 
		this.previousPositioning = this.elStyle.getPosition();
		this.previousLeft = this.elStyle.getLeft();
		this.previousTop = this.elStyle.getTop();
		this.previousBottom = this.elStyle.getBottom();

		this.previousMarginTop = this.elStyle.getMarginTop();
		this.previousMarginRight = this.elStyle.getMarginRight();
		this.previousMarginBottom = this.elStyle.getMarginBottom();
		this.previousMarginLeft = this.elStyle.getMarginLeft();

		Offset elPos = $el.offset();
		this.topAbsoluteOffset = elPos.top;

		int positionedY = 0;
		int positionedLeft = elPos.left;

		String marginTop, marginBottom, marginLeft;
		marginTop = this.$el.css(CSS.MARGIN_TOP, true);
		marginBottom = this.$el.css(CSS.MARGIN_BOTTOM, true);
		marginLeft = this.$el.css(CSS.MARGIN_LEFT, true);

		if(top) {
			positionedY -= Integer.parseInt(marginTop.replace("px", ""));
		}
		else {
			positionedY -= Integer.parseInt(marginBottom.replace("px", ""));
		}
		positionedLeft -= Integer.parseInt(marginLeft.replace("px", ""));

		// Do we need to create a ghost element?
		String actualPositioning = this.$el.css(CSS.POSITION, true);
		if(actualPositioning.equals("relative") || actualPositioning.equals("static")) {
			// Now we need to measure some vitals on the Element before we change its
			// positioning. I'm measuring margins separately to dimensions, as there
			// might be corner cases where margin collapsing would occur, resulting
			// in a different page flow if we set up our ghost element with its width
			// and height also including margins.
			int width, height;
			String marginRight;
			String display;

			width = this.$el.outerWidth();
			height = this.$el.outerHeight();
			marginRight = this.$el.css(CSS.MARGIN_RIGHT, true);
			display = this.$el.css(CSS.DISPLAY, true);

			// Now we can setup the ghost element.
			this.ghostElement = Document.get().createDivElement();
			Style ghostElementStyle = this.ghostElement.getStyle();

			ghostElementStyle.setWidth(width, Unit.PX);
			ghostElementStyle.setHeight(height, Unit.PX);
			ghostElementStyle.setProperty("margin", marginTop + " " + marginRight + " " + marginBottom + " " + marginLeft);
			ghostElementStyle.setVisibility(Visibility.HIDDEN);
			ghostElementStyle.setProperty("display", display);
			ghostElementStyle.setProperty("verticalAlign", "middle");
		}

		this.elStyle.setPosition(Position.FIXED);
		this.elStyle.setLeft(positionedLeft, Unit.PX);
		
		if(top) {
			this.elStyle.setProperty("bottom", "auto");
			this.elStyle.setTop(positionedY, Unit.PX);
		}
		else {
			this.elStyle.setProperty("top", "auto");
			this.elStyle.setBottom(positionedY, Unit.PX);
		}

		if(this.ghostElement != null) {
			this.el.getParentElement().insertAfter(this.ghostElement, this.el);
		}

		this.detachedTop = top;
		this.detached = true;
	}

	private void reattach() {
		if(this.ghostElement != null) {
			this.ghostElement.removeFromParent();
			this.ghostElement = null;
		}

		this.elStyle.setProperty("position", this.previousPositioning);
		this.elStyle.setProperty("left", this.previousLeft);
		this.elStyle.setProperty("top", this.previousTop);
		this.elStyle.setProperty("bottom", this.previousBottom);

		/*this.elStyle.setProperty("marginTop", this.previousMarginTop);
		this.elStyle.setProperty("marginRight", this.previousMarginRight);
		this.elStyle.setProperty("marginBottom", this.previousMarginBottom);
		this.elStyle.setProperty("marginLeft", this.previousMarginLeft);*/

		this.detached = false;
	}

	@Override
	public void onResize(ResizeEvent event) {
		this.viewportWidth = event.getWidth();
		this.viewportHeight = event.getHeight();

		GWT.log("Viewport resized: " + this.viewportWidth + "x" + this.viewportHeight);

		this.checkAttachment();
	}

	@Override
	public void onWindowScroll(ScrollEvent event) {
		this.scrollLeft = event.getScrollLeft();
		this.scrollTop = event.getScrollTop();

		this.checkAttachment();
	}
}
