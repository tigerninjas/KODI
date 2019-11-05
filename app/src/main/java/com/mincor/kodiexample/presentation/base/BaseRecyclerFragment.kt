package com.mincor.kodiexample.presentation.base

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.diff.FastAdapterDiffUtil
import com.mikepenz.fastadapter.items.AbstractItem
import com.mikepenz.fastadapter.ui.items.ProgressItem
import com.mincor.kodiexample.common.EndlessRecyclerViewScrollListener
import com.mincor.kodiexample.common.ScrollPosition
import com.mincor.kodiexample.common.unsafeLazy
import com.rasalexman.sticky.core.IStickyPresenter
import com.rasalexman.sticky.core.IStickyView

abstract class BaseRecyclerFragment<I, P> : BaseFragment<P>()
        where I : BaseRecyclerUI<*>, P : IStickyPresenter<out IStickyView> {

    abstract val recyclerViewId: Int

    private var isLoading: Boolean = false

    protected var recycler: RecyclerView? = null


    // current iterating item
    protected var currentItem: AbstractItem<*>? = null

    // layout manager for recycler
    protected open var recyclerLayoutManager: RecyclerView.LayoutManager? = null
    // направление размещения элементов в адаптере
    protected open val layoutManagerOrientation: Int = LinearLayoutManager.VERTICAL
    // бесконечный слушатель слушатель скролла
    private var scrollListener: EndlessRecyclerViewScrollListener? = null
    // custom decorator
    protected open var itemDecoration: RecyclerView.ItemDecoration? = null

    // main adapter items holder
    private val itemAdapter: ItemAdapter<AbstractItem<*>> by unsafeLazy { ItemAdapter<AbstractItem<*>>() }
    // save our FastAdapter
    protected val mFastItemAdapter: FastAdapter<*> by unsafeLazy { FastAdapter.with(itemAdapter) }

    // последняя сохраненная позиция (index & offset) прокрутки ленты
    protected val previousPosition: ScrollPosition = ScrollPosition()
    // крутилка прогресса)
    private val progressItem by unsafeLazy { ProgressItem().apply { isEnabled = false } }
    // корличесвто элементов до того как пойдет запрос на скролл пагинацию
    protected open val visibleScrollCount = 5
    //
    protected open val needScroll: Boolean = false

    //
    open val onLoadNextPageHandler: ((Int) -> Unit)? = null
    open val onLoadNextHandler: (() -> Unit)? = null
    open val onItemClickHandler: ((I) -> Unit)? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycler = view.findViewById(recyclerViewId)

        setRVLayoutManager()     // менеджер лайаута
        setItemDecorator()       // декорации
        setRVCAdapter()          // назначение
        addClickListener()
        addEventHook()           // для нажатия внутри айтемов
        if (needScroll) {
            setRVCScroll()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        savePreviousPosition()
        clearFastAdapter()
        clearRecycler()

        recyclerLayoutManager = null
        scrollListener = null
        itemDecoration = null
        recycler = null
        scrollListener = null
        currentItem = null
    }

    override fun showLoading() {
        hideLoading()
        itemAdapter.add(progressItem)
        isLoading = true
    }

    override fun hideLoading() {
        val position = itemAdapter.getAdapterPosition(progressItem)
        if (position > -1) itemAdapter.remove(position)
        isLoading = false
    }

    open fun showItems(list: List<I>) {
        hideLoading()
        if (list.isNotEmpty()) {
            FastAdapterDiffUtil[itemAdapter] = list
            scrollToTop()
        }
    }

    open fun addNewItems(list: List<I>) {
        hideLoading()
        if (list.isNotEmpty()) {
            FastAdapterDiffUtil[itemAdapter] = FastAdapterDiffUtil.calculateDiff(itemAdapter, list)
            scrollToTop()
        }
    }

    open fun addItems(list: List<I>) {
        hideLoading()
        if (list.isNotEmpty()) {
            itemAdapter.add(list)
        }
    }

    open fun showError(message: String?) {
        currentItem = null
        hideLoading()
        message?.let { errorMessage ->
            showToast(errorMessage)
        }
    }

    // менеджер лайаута
    protected open fun setRVLayoutManager() {
        recycler?.apply {
            recyclerLayoutManager = LinearLayoutManager(context, layoutManagerOrientation, false)
            layoutManager = recyclerLayoutManager
        }
    }

    // промежутки в адаптере
    protected open fun setItemDecorator() {
        if (recycler?.itemDecorationCount == 0) {
            itemDecoration?.let { recycler?.addItemDecoration(it) }
        }
    }

    protected open fun addClickListener() {
        mFastItemAdapter.onClickListener = { _, _, item, _ ->
            onItemClickHandler?.invoke(item as I)
            false
        }
    }

    //назначаем адаптеры
    protected open fun setRVCAdapter(isFixedSizes: Boolean = false) {
        recycler?.adapter ?: let {
            recycler?.setHasFixedSize(isFixedSizes)
            recycler?.swapAdapter(mFastItemAdapter, true)
        }
    }

    //
    protected open fun addEventHook() {}

    // слушатель бесконечная прокрутка
    protected open fun setRVCScroll() {
        scrollListener = scrollListener
                ?: object : EndlessRecyclerViewScrollListener(recyclerLayoutManager, visibleScrollCount) {
                    override fun onLoadMore(page: Int, totalItemsCount: Int) {
                        onLoadNextHandler?.invoke() ?: onLoadNextPageHandler?.invoke(page)
                    }
                }
        recycler?.addOnScrollListener(scrollListener!!)
    }

    // если хотим сохранить последнюю проскролленную позицию
    protected open fun savePreviousPosition() {
        recycler?.let { rec ->
            previousPosition.let { scrollPosition ->
                val v = rec.getChildAt(0)
                scrollPosition.index = (rec.layoutManager as? LinearLayoutManager?)?.findFirstVisibleItemPosition()
                        ?: 0
                scrollPosition.top = v?.let { it.top - rec.paddingTop } ?: 0
            }
        }
    }

    // прокручиваем к ранее выбранным элементам
    open fun applyScrollPosition() {
        recycler?.let { rec ->
            stopRecyclerScroll()
            previousPosition.let {
                (rec.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(it.index, it.top)
            }
        }
    }

    // скролл к верхней записи
    protected fun scrollToTop() {
        stopRecyclerScroll()
        // сбрасываем прокрутку
        previousPosition.drop()
        // применяем позицию
        applyScrollPosition()
    }

    private fun stopRecyclerScroll() {
        // останавливаем прокрутку
        recycler?.stopScroll()
    }

    private fun clearAdapter() {
        scrollListener?.resetState()
        itemAdapter.clear()
        mFastItemAdapter.notifyAdapterDataSetChanged()
        mFastItemAdapter.notifyDataSetChanged()
    }

    private fun clearFastAdapter() {
        clearAdapter()
        mFastItemAdapter.apply {
            onClickListener = null
            eventHooks.clear()
        }
    }

    private fun clearRecycler() {
        recycler?.apply {
            removeAllViews()
            removeAllViewsInLayout()
            adapter = null
            layoutManager = null
            itemAnimator = null
            clearOnScrollListeners()
            recycledViewPool.clear()

            itemDecoration?.let {
                removeItemDecoration(it)
            }
            itemDecoration = null
        }
    }

    protected fun removeCurrentItemFromAdapter() {
        currentItem?.let {
            itemAdapter.getAdapterPosition(it).let { position ->
                itemAdapter.remove(position)
                currentItem = null
            }
        }
    }

}