package me.ly.tools.mybatis.entity;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.alibaba.fastjson.JSON;

/**
 * 分页实体类
 *
 * @author Created by LiYao on 2017-03-04 20:54.
 */
@SuppressWarnings({ "unused", "WeakerAccess", "JavaDoc" })
public class Pagination {

	private int pageSize = 20;// 每页显示多少
	private int pageIndex = 1;// 第几页
	private int totalCount;// 数据总条数
	private int pageCount;// 一共多少页
	private List<?> list;
	private int begin = 1;// 开始页码
	private int end = 10;// 结束页码

	private int offset;// 偏移量

	public int getOffset() {
		return (this.getPageIndex() - 1) * this.getPageSize();
	}

	public int getBegin() {
		return begin;
	}

	public int getEnd() {
		return end;
	}

	public void setBeginAndEnd(int index) {
		this.end = this.end > this.getPageCount() ? this.getPageCount() : this.end;
		int a = (this.end - this.begin) / 2 + 1;
		int interval = this.end - this.begin;
		this.begin = this.begin + index - a;
		this.end = end + index - a;
		if (this.begin < 1) {
			this.begin = 1;
			this.end = interval + 1;
		}
		if (this.end > this.getPageCount()) {
			this.end = this.getPageCount();
			this.begin = this.end - interval;
		}
	}

	/**
	 * 获得查询到的对象集合
	 *
	 * @return List<T>
	 */
	public List<?> getList() {
		return list == null ? new ArrayList<>() : this.list;
	}

	/**
	 * 存入查询到的对象集合
	 *
	 * @param list
	 */
	public void setList(List<?> list) {
		this.list = list;
	}

	/**
	 * 每页显示多少
	 *
	 * @return pageSize
	 */
	public int getPageSize() {
		return pageSize;
	}

	public void setPageSize(int pageSize) {
		if (pageSize != 0) {
			this.pageSize = pageSize;
		}
	}

	public void setPageSize(String pageSize) {
		if (StringUtils.isNotBlank(pageSize)) {
			this.pageSize = Integer.parseInt(pageSize);
		}
	}

	/**
	 * 获取第几页
	 *
	 * @return pageIndex
	 */
	public int getPageIndex() {
		return pageIndex;
	}

	/**
	 * 设置第几页
	 *
	 */
	public void setPageIndex(String pageIndex) {
		int index = 1;
		if (pageIndex == null || "".equals(pageIndex.trim())) {
			pageIndex = "1";
		}
		try {
			index = Integer.parseInt(pageIndex);
		} catch (NumberFormatException ignored) {
		}
		this.setPageIndex(index);
	}

	public void setPageIndex(int pageIndex) {
		if (pageIndex < 1) {
			pageIndex = 1;
		}
		if (this.getPageCount() > 0 && pageIndex >= this.getPageCount()) {
			if (this.getPageCount() > 0) {
				pageIndex = this.getPageCount();
			}
		}
		this.pageIndex = pageIndex;
	}

	/**
	 * 设置总条数
	 *
	 * @param totalCount
	 */
	public void setTotalCount(int totalCount) {
		this.totalCount = totalCount;
		this.pageCount = (this.totalCount + this.pageSize - 1) / this.pageSize;
		this.setPageIndex(String.valueOf(this.pageIndex));
		this.setBeginAndEnd(this.pageIndex);
	}

	/**
	 * 数据总条数
	 * 
	 * @return
	 */
	public int getTotalCount() {
		return totalCount;
	}

	/**
	 * 获得总页数
	 *
	 */
	public int getPageCount() {
		return pageCount;
	}

	/**
	 * 第一页
	 * 
	 * @return 第一页
	 */
	public int getFirstPage() {
		return 1;
	}

	/**
	 * 最后一页
	 * 
	 * @return 最后一页
	 */
	public int getLastPage() {
		return this.getPageCount();
	}

	/**
	 * 下一页
	 * 
	 * @return 下一页
	 */
	public int nextPage() {
		return this.pageIndex + 1 > this.getPageCount() ? this.getPageCount() : this.pageIndex + 1;
	}

	/**
	 * 上一页
	 * 
	 * @return 上一页
	 */
	public int prevPage() {
		return this.pageIndex - 1 < 1 ? 1 : this.pageIndex - 1;
	}

	@Override
	public String toString() {
		return JSON.toJSONString(this);
	}
}
