package com.shiji.libary.pulltorefresh;

import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * A generic, customizable Android ListView implementation that has 'Pull to Refresh' functionality.
 * <p/>
 * This ListView can be used in place of the normal Android android.widget.ListView class.
 * <p/>
 * Users of this class should implement OnRefreshListener and call setOnRefreshListener(..)
 * to get notified on refresh events. The using class should call onRefreshComplete() when
 * refreshing is finished.
 * <p/>
 * The using class can call setRefreshing() to set the state explicitly to refreshing. This
 * is useful when you want to show the spinner and 'Refreshing' text when the
 * refresh was not triggered by 'Pull to Refresh', for example on start.
 * <p/>
 * For more information, visit the project page:
 * https://github.com/erikwt/PullToRefresh-ListView
 *
 * @author Erik Wallentinsen <dev+ptr@erikw.eu>
 * @version 1.3.0
 */
public class PullToRefreshListView extends ListView{

    private static final float PULL_RESISTANCE                 = 2.2f;
    private static final int   BOUNCE_ANIMATION_DURATION       = 1200;
    private static final int   BOUNCE_ANIMATION_DELAY          = 100;
    private static final float BOUNCE_OVERSHOOT_TENSION        = 1.0f;
    private static final int   ROTATE_ARROW_ANIMATION_DURATION = 250;

    private static enum State{
        PULL_TO_REFRESH,
        RELEASE_TO_REFRESH,
        REFRESHING
    }

    /**
     * Interface to implement when you want to get notified of 'pull to refresh'
     * events.
     * Call setOnRefreshListener(..) to activate an OnRefreshListener.
     */
    public interface OnRefreshListener{

        /**
         * Method to be called when a refresh is requested
         */
        public void onRefresh();
    }
    public interface OnLoadingPageListener{
    	public void loadPage(int currentPage);
    }

    private static int measuredHeaderHeight;

    private boolean scrollbarEnabled;
    private boolean bounceBackHeader;
    private boolean lockScrollWhileRefreshing;
    private boolean showLastUpdatedText;
    private String  pullToRefreshText;
    private String  releaseToRefreshText;
    private String  refreshingText;
    private String  lastUpdatedText;
    private SimpleDateFormat lastUpdatedDateFormat = new SimpleDateFormat("dd/MM HH:mm");

    private float                   previousY;
    private int                     headerPadding;
    private boolean                 hasResetHeader;
    private long                    lastUpdated = -1;
    private State                   state;
    private LinearLayout            headerContainer;
    private RelativeLayout          header;
    private RotateAnimation         flipAnimation;
    private RotateAnimation         reverseFlipAnimation;
    private ImageView               image;
    private ProgressBar             spinner;
    private TextView                text;
    private TextView                lastUpdatedTextView;
    private OnItemClickListener     onItemClickListener;
    private OnItemLongClickListener onItemLongClickListener;
    private OnRefreshListener       onRefreshListener;
    private float mScrollStartY;
    private final int IDLE_DISTANCE = 5;
    
    //有关分页
    private OnLoadingPageListener 	onLoadingPageListerer;
    private int currentMaxPage = 1;//当前页,当前最大页，区别于currentPage ，当前需要获取的页面，也就是当最大分页数+1
    private boolean isDividing;
    private boolean diviable;//只有分页条件满足且不是正在分页时候才可以进行下次分页
    private boolean isHaveNoMore;//是否已经没有更多
    private TextView footerTv;//分页标识
    private ProgressBar footerPb;//分页等待旋转
    
    public PullToRefreshListView(Context context){
        super(context);
        init();
    }

    public PullToRefreshListView(Context context, AttributeSet attrs){
        super(context, attrs);
        init();
    }

    public PullToRefreshListView(Context context, AttributeSet attrs, int defStyle){
        super(context, attrs, defStyle);
        init();
    }

    @Override
    public void setOnItemClickListener(OnItemClickListener onItemClickListener){
        this.onItemClickListener = onItemClickListener;
    }

    @Override
    public void setOnItemLongClickListener(OnItemLongClickListener onItemLongClickListener){
        this.onItemLongClickListener = onItemLongClickListener;
    }

    /**
     * Activate an OnRefreshListener to get notified on 'pull to refresh'
     * events.
     *
     * @param onRefreshListener The OnRefreshListener to get notified
     */
    public void setOnRefreshListener(OnRefreshListener onRefreshListener){
        this.onRefreshListener = onRefreshListener;
    }

    /**
     * @return If the list is in 'Refreshing' state
     */
    public boolean isRefreshing(){
        return state == State.REFRESHING;
    }

    /**
     * Default is false. When lockScrollWhileRefreshing is set to true, the list
     * cannot scroll when in 'refreshing' mode. It's 'locked' on refreshing.
     *
     * @param lockScrollWhileRefreshing
     */
    public void setLockScrollWhileRefreshing(boolean lockScrollWhileRefreshing){
        this.lockScrollWhileRefreshing = lockScrollWhileRefreshing;
    }

    /**
     * Default is false. Show the last-updated date/time in the 'Pull ro Refresh'
     * header. See 'setLastUpdatedDateFormat' to set the date/time formatting.
     *
     * @param showLastUpdatedText
     */
    public void setShowLastUpdatedText(boolean showLastUpdatedText){
        this.showLastUpdatedText = showLastUpdatedText;
        if(!showLastUpdatedText) lastUpdatedTextView.setVisibility(View.GONE);
    }

    /**
     * Default: "dd/MM HH:mm". Set the format in which the last-updated
     * date/time is shown. Meaningless if 'showLastUpdatedText == false (default)'.
     * See 'setShowLastUpdatedText'.
     *
     * @param lastUpdatedDateFormat
     */
    public void setLastUpdatedDateFormat(SimpleDateFormat lastUpdatedDateFormat){
        this.lastUpdatedDateFormat = lastUpdatedDateFormat;
    }

    /**
     * Explicitly set the state to refreshing. This
     * is useful when you want to show the spinner and 'Refreshing' text when
     * the refresh was not triggered by 'pull to refresh', for example on start.
     */
    public void setRefreshing(){
        state = State.REFRESHING;
        scrollTo(0, 0);
        setUiRefreshing();
        setHeaderPadding(0);
    }

    /**
     * Set the state back to 'pull to refresh'. Call this method when refreshing
     * the data is finished.
     */
    public void onRefreshComplete(){
        state = State.PULL_TO_REFRESH;
        resetHeader();
        lastUpdated = System.currentTimeMillis();
    }
    
    /**
     * 当加载listview数据完毕时需要调用的
     */
    public void onLoadingFinish(){
    	footerTv.setText(R.string.more);
		footerPb.setVisibility(View.GONE);
		isDividing = false;
    }
    
    /**
     * 当列表已经没有更多信息时
     */
    public void onHaveNoMore(){
    	footerTv.setText(R.string.have_no_more);
		footerPb.setVisibility(View.GONE);
    	isHaveNoMore = true;
    }

    /**
     * Change the label text on state 'Pull to Refresh'
     *
     * @param pullToRefreshText Text
     */
    public void setTextPullToRefresh(String pullToRefreshText){
        this.pullToRefreshText = pullToRefreshText;
        if(state == State.PULL_TO_REFRESH){
            text.setText(pullToRefreshText);
        }
    }

    /**
     * Change the label text on state 'Release to Refresh'
     *
     * @param releaseToRefreshText Text
     */
    public void setTextReleaseToRefresh(String releaseToRefreshText){
        this.releaseToRefreshText = releaseToRefreshText;
        if(state == State.RELEASE_TO_REFRESH){
            text.setText(releaseToRefreshText);
        }
    }

    /**
     * Change the label text on state 'Refreshing'
     *
     * @param refreshingText Text
     */
    public void setTextRefreshing(String refreshingText){
        this.refreshingText = refreshingText;
        if(state == State.REFRESHING){
            text.setText(refreshingText);
        }
    }
    
    
    /*
     * 分页getter setter
     */
    public OnLoadingPageListener getOnLoadingPageListerer() {
		return onLoadingPageListerer;
	}

	public void setOnLoadingPageListerer(OnLoadingPageListener onLoadingPageListerer) {
		this.onLoadingPageListerer = onLoadingPageListerer;
	}

	public int getCurrentMaxPage() {
		return currentMaxPage;
	}
	public void setCurrentMaxPage(int currentMaxPage){
		this.currentMaxPage = currentMaxPage;
	}
	

	public boolean isDiviable() {
		return diviable;
	}

	
	private void init(){
    	
        setVerticalFadingEdgeEnabled(false);

        headerContainer = (LinearLayout) LayoutInflater.from(getContext()).inflate(R.layout.ptr_header, null);
        header = (RelativeLayout) headerContainer.findViewById(R.id.ptr_id_header);
        text = (TextView) header.findViewById(R.id.ptr_id_text);
        lastUpdatedTextView = (TextView) header.findViewById(R.id.ptr_id_last_updated);
        image = (ImageView) header.findViewById(R.id.ptr_id_image);
        spinner = (ProgressBar) header.findViewById(R.id.ptr_id_spinner);

        pullToRefreshText = getContext().getString(R.string.ptr_pull_to_refresh);
        releaseToRefreshText = getContext().getString(R.string.ptr_release_to_refresh);
        refreshingText = getContext().getString(R.string.ptr_refreshing);
        lastUpdatedText = getContext().getString(R.string.ptr_last_updated);

        flipAnimation = new RotateAnimation(0, -180, RotateAnimation.RELATIVE_TO_SELF, 0.5f, RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        flipAnimation.setInterpolator(new LinearInterpolator());
        flipAnimation.setDuration(ROTATE_ARROW_ANIMATION_DURATION);
        flipAnimation.setFillAfter(true);
        
        reverseFlipAnimation = new RotateAnimation(-180, 0, RotateAnimation.RELATIVE_TO_SELF, 0.5f, RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        reverseFlipAnimation.setInterpolator(new LinearInterpolator());
        reverseFlipAnimation.setDuration(ROTATE_ARROW_ANIMATION_DURATION);
        reverseFlipAnimation.setFillAfter(true);
        
        addHeaderView(headerContainer);
        setState(State.PULL_TO_REFRESH);
        scrollbarEnabled = isVerticalScrollBarEnabled();

        ViewTreeObserver vto = header.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new PTROnGlobalLayoutListener());
        //如果加载了onLoadingListener才需显示底部获取更多
        if(true){
        	View footer = LayoutInflater.from(getContext()).inflate(R.layout.more_footer, null);
        	Resources r = getResources();
        	float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 50, r.getDisplayMetrics());
        	footer.setLayoutParams(new AbsListView.LayoutParams(r.getDisplayMetrics().widthPixels, (int) px));
        	addFooterView(footer,null,false);        
        	footerTv = (TextView) footer.findViewById(R.id.more_footer_text);
        	footerPb = (ProgressBar) footer.findViewById(R.id.more_footer_progress);
        }
        	

        super.setOnItemClickListener(new PTROnItemClickListener());
        super.setOnItemLongClickListener(new PTROnItemLongClickListener());
    }
	@Override
	public void setAdapter(ListAdapter adapter) {
		//当数据绑定之后才可以滑动
		super.setAdapter(adapter);
		
    	super.setOnScrollListener(new OnScrollListener() {
    		
    		@Override
    		public void onScrollStateChanged(AbsListView view, int scrollState) {
    			
    			if(onLoadingPageListerer!=null&&!isDividing&&diviable&&scrollState==OnScrollListener.SCROLL_STATE_IDLE){
    				System.out.println("进行分页");
    				isDividing = true;
    				onLoadingPageListerer.loadPage(++currentMaxPage);
    				footerTv.setText(getResources().getString(R.string.loading));
    				footerPb.setVisibility(View.VISIBLE);
    			}else{
    			}
    			
    		}
    		
    		@Override
    		public void onScroll( AbsListView view, int firstVisibleItem,
    				int visibleItemCount, int totalItemCount) {
    			
    			if(firstVisibleItem+visibleItemCount==totalItemCount&&!isDividing&&!isHaveNoMore){
    				diviable = true;
    			}else{
    				diviable = false;
    			}
    		}
    	});
		
	}
    private void setHeaderPadding(int padding){
        headerPadding = padding;

        MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) header.getLayoutParams();
        mlp.setMargins(0, 0, 0,Math.round(padding));
        header.setLayoutParams(mlp);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event){
        if(lockScrollWhileRefreshing
                && (state == State.REFRESHING || getAnimation() != null && !getAnimation().hasEnded())){
            return true;
        }

        switch(event.getAction()){
            case MotionEvent.ACTION_DOWN:
                if(getFirstVisiblePosition() == 0){
                	previousY = event.getY();
                }
                else {
                	previousY = -1;
                }
                
                // Remember where have we started
                mScrollStartY = event.getY();
                
                break;

            case MotionEvent.ACTION_UP:
                if(previousY != -1 && (state == State.RELEASE_TO_REFRESH || getFirstVisiblePosition() == 0)){
                    switch(state){
                        case RELEASE_TO_REFRESH:
                            setState(State.REFRESHING);
                            bounceBackHeader();

                            break;

                        case PULL_TO_REFRESH:
                            resetHeader();
                            break;
                    }
                }else{
                	
                }
                
                break;

            case MotionEvent.ACTION_MOVE:
                if(previousY != -1 && getFirstVisiblePosition() == 0 && Math.abs(mScrollStartY-event.getY()) > IDLE_DISTANCE){
                    float y = event.getY();
                    float diff = y - previousY;
                    if(diff > 0) diff /= PULL_RESISTANCE;
                    previousY = y;

                    int newHeaderPadding = Math.max(Math.round(headerPadding + diff), -header.getHeight());

                    if(newHeaderPadding != headerPadding && state != State.REFRESHING){
                        setHeaderPadding(newHeaderPadding);

                        if(state == State.PULL_TO_REFRESH && headerPadding > 0&&!isDividing){
                            setState(State.RELEASE_TO_REFRESH);

                            image.clearAnimation();
                            image.startAnimation(flipAnimation);
                        }else if(state == State.RELEASE_TO_REFRESH && headerPadding < 0&&!isDividing){
                            setState(State.PULL_TO_REFRESH);

                            image.clearAnimation();
                            image.startAnimation(reverseFlipAnimation);
                        }
                    }
                }

                break;
        }

        return super.onTouchEvent(event);
    }

    private void bounceBackHeader(){
        int yTranslate = state == State.REFRESHING ?
                header.getHeight() - headerContainer.getHeight() :
                -headerContainer.getHeight() - headerContainer.getTop() + getPaddingTop();;

        TranslateAnimation bounceAnimation = new TranslateAnimation(
                TranslateAnimation.ABSOLUTE, 0,
                TranslateAnimation.ABSOLUTE, 0,	
                TranslateAnimation.ABSOLUTE, 0,
                TranslateAnimation.ABSOLUTE, yTranslate);

        bounceAnimation.setDuration(BOUNCE_ANIMATION_DURATION);
        bounceAnimation.setFillEnabled(true);
        bounceAnimation.setFillAfter(false);
        bounceAnimation.setFillBefore(true);
        bounceAnimation.setInterpolator(new OvershootInterpolator(BOUNCE_OVERSHOOT_TENSION));
        bounceAnimation.setAnimationListener(new HeaderAnimationListener(yTranslate));

        startAnimation(bounceAnimation);
    }

    private void resetHeader(){
        if(getFirstVisiblePosition() > 0){
            setHeaderPadding(-header.getHeight());
            setState(State.PULL_TO_REFRESH);
            return;
        }

        if(getAnimation() != null && !getAnimation().hasEnded()){
            bounceBackHeader = true;
        }else{
            bounceBackHeader();
        }
    }

    private void setUiRefreshing(){
        spinner.setVisibility(View.VISIBLE);
        image.clearAnimation();
        image.setVisibility(View.INVISIBLE);
        text.setText(refreshingText);
    }

    private void setState(State state){
        this.state = state;
        switch(state){
            case PULL_TO_REFRESH:
                spinner.setVisibility(View.INVISIBLE);
                image.setVisibility(View.VISIBLE);
                text.setText(pullToRefreshText);

                if(showLastUpdatedText && lastUpdated != -1){
                    lastUpdatedTextView.setVisibility(View.VISIBLE);
                    lastUpdatedTextView.setText(String.format(lastUpdatedText, lastUpdatedDateFormat.format(new Date(lastUpdated))));
                }

                break;

            case RELEASE_TO_REFRESH:
                spinner.setVisibility(View.INVISIBLE);
                image.setVisibility(View.VISIBLE);
                text.setText(releaseToRefreshText);
                break;

            case REFRESHING:
                setUiRefreshing();

                lastUpdated = System.currentTimeMillis();
                if(onRefreshListener == null){
                    setState(State.PULL_TO_REFRESH);
                }else{
                    onRefreshListener.onRefresh();
                }

                break;
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt){
        super.onScrollChanged(l, t, oldl, oldt);

        if(!hasResetHeader){
            if(measuredHeaderHeight > 0 && state != State.REFRESHING){
                setHeaderPadding(-measuredHeaderHeight);
            }

            hasResetHeader = true;
        }
    }

    private class HeaderAnimationListener implements AnimationListener{

        private int height, translation;
        private State stateAtAnimationStart;

        public HeaderAnimationListener(int translation){
            this.translation = translation;
        }

        @Override
        public void onAnimationStart(Animation animation){
            stateAtAnimationStart = state;

            android.view.ViewGroup.LayoutParams lp = getLayoutParams();
            height = lp.height;
            lp.height = getHeight() - translation;
            setLayoutParams(lp);

            if(scrollbarEnabled){
                setVerticalScrollBarEnabled(false);
            }
        }

        @Override
        public void onAnimationEnd(Animation animation){
            setHeaderPadding(stateAtAnimationStart == State.REFRESHING ? 0 : -measuredHeaderHeight - headerContainer.getTop());
            setSelection(0);

            android.view.ViewGroup.LayoutParams lp = getLayoutParams();
            lp.height = height;
            setLayoutParams(lp);

            if(scrollbarEnabled){
                setVerticalScrollBarEnabled(true);
            }

            if(bounceBackHeader){
                bounceBackHeader = false;

                postDelayed(new Runnable(){

                    @Override
                    public void run(){
                        resetHeader();
                    }
                }, BOUNCE_ANIMATION_DELAY);
            }else if(stateAtAnimationStart != State.REFRESHING){
                setState(State.PULL_TO_REFRESH);
            }
        }

        @Override
        public void onAnimationRepeat(Animation animation){}
    }

    private class PTROnGlobalLayoutListener implements OnGlobalLayoutListener{

        @Override
        public void onGlobalLayout(){
            int initialHeaderHeight = header.getHeight();

            if(initialHeaderHeight > 0){
                measuredHeaderHeight = initialHeaderHeight;

                if(measuredHeaderHeight > 0 && state != State.REFRESHING){
                    setHeaderPadding(-measuredHeaderHeight);
                    requestLayout();
                }
            }

            getViewTreeObserver().removeGlobalOnLayoutListener(this);
        }
    }

    private class PTROnItemClickListener implements OnItemClickListener{

        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long id){
            hasResetHeader = false;

            if(onItemClickListener != null && state == State.PULL_TO_REFRESH){
                // Passing up onItemClick. Correct position with the number of header views
                onItemClickListener.onItemClick(adapterView, view, position - getHeaderViewsCount(), id);
            }
        }
    }

    private class PTROnItemLongClickListener implements OnItemLongClickListener{

        @Override
        public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id){
            hasResetHeader = false;

            if(onItemLongClickListener != null && state == State.PULL_TO_REFRESH){
                // Passing up onItemLongClick. Correct position with the number of header views
                return onItemLongClickListener.onItemLongClick(adapterView, view, position - getHeaderViewsCount(), id);
            }

            return false;
        }
    }
}
