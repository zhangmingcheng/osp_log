package com.osp.log.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.ExtendedBounds;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram.Bucket;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.joda.time.DateTimeZone;
import org.springframework.stereotype.Service;

import com.osp.log.model.Page;
import com.osp.log.model.TomcatModel;
import com.osp.log.service.BaseESService;
import com.osp.log.service.TomcatService;
import com.osp.log.util.DateUtil;
import com.osp.log.util.ESUtil;

@Service
public class TomcatServiceImpl extends BaseESService<TomcatModel> implements TomcatService {

	public static final String INDEX_NAME = "logstash-apacheaccesslog*"; // 索引名称
	public static final String INDEX_TYPE = "logs"; // 索引类型

	public TomcatServiceImpl() {
		super();
	}

	@Override
	public TomcatModel tomcatTimeSearch(int day) {
		// 查询索引
		TransportClient client = this.getClient();
		SearchRequestBuilder srb = client.prepareSearch(this.getIndexName()).setTypes(getIndexType());

		// 组装分组
		DateHistogramAggregationBuilder dateAgg = AggregationBuilders.dateHistogram("dateagg");
		// 定义分组的日期字段
		dateAgg.field("@timestamp");
		ExtendedBounds extendedBounds = new ExtendedBounds(DateUtil.getPastDate(day), DateUtil.getDate());
		dateAgg.extendedBounds(extendedBounds);
		dateAgg.dateHistogramInterval(DateHistogramInterval.DAY);
		DateTimeZone timeZone = DateTimeZone.forID("Asia/Shanghai");
		dateAgg.timeZone(timeZone);
		dateAgg.format("yyyy-MM-dd");

		RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("@timestamp").from(DateUtil.getPastDate(day))
				.to(DateUtil.getDate()).includeLower(true) // 包括下界
				.includeUpper(true); // 包括上界

		// 组装请求
		srb.setQuery(rangeQueryBuilder).addAggregation(dateAgg);

		// 查询
		SearchResponse response = srb.execute().actionGet();
		// 获取一级聚合数据
		Histogram h = response.getAggregations().get("dateagg");
		// 得到一级聚合结果里面的分桶集合
		// org.elasticsearch.search.aggregations.bucket.histogram
		List<Bucket> buckets = (List<Bucket>) h.getBuckets();

		TomcatModel tomcat = new TomcatModel();
		// for(int i=day-buckets.size(); i>0 ; i--) {
		// tomcat.addKey(DateUtil.getPastDate(i));
		// tomcat.addValue(0);
		// }
		// 遍历分桶集
		for (Bucket b : buckets) {
			org.joda.time.DateTime key = (org.joda.time.DateTime) b.getKey();
			tomcat.addKey(key.getMonthOfYear() + "-" + key.getDayOfMonth());
			tomcat.addValue((int) b.getDocCount());
		}
		return tomcat;
	}

	@Override
	public TomcatModel tomcatRequest() {
		// 查询索引
		TransportClient client = getClient();
		TomcatModel tomcat = new TomcatModel();
		MatchQueryBuilder queryBuilderGet = QueryBuilders.matchQuery("verb", "GET");
		// GET POST ..
		SearchResponse responseGet = client.prepareSearch(getIndexName()).setTypes(getIndexType())
				.setQuery(queryBuilderGet).execute().actionGet();
		// System.out.println("execute time = " + responseGet.getTook());
		SearchHits myhitsGet = responseGet.getHits();
		// System.out.println("execute Hits = " + myhitsGet.getTotalHits());
		tomcat.addKey("GET");
		tomcat.addValue(myhitsGet.getTotalHits());

		MatchQueryBuilder queryBuilderPost = QueryBuilders.matchQuery("verb", "POST");
		SearchResponse responsePost = client.prepareSearch(getIndexName()).setTypes(getIndexType())
				.setQuery(queryBuilderPost).execute().actionGet();
		SearchHits myhitsPost = responsePost.getHits();
		tomcat.addKey("POST");
		tomcat.addValue(myhitsPost.getTotalHits());

		MatchQueryBuilder queryBuilderPut = QueryBuilders.matchQuery("verb", "PUT");
		SearchResponse responsePut = client.prepareSearch(getIndexName()).setQuery(queryBuilderPut).execute()
				.actionGet();
		SearchHits myhitsPut = responsePut.getHits();
		tomcat.addKey("PUT");
		tomcat.addValue(myhitsPut.getTotalHits());
		return tomcat;
	}

	/**
	 * 客户端访问次数统计-前10
	 */
	@Override
	public List<TomcatModel> clientRequestCount() {
		List<TomcatModel> list = new ArrayList<TomcatModel>();
		TransportClient client = this.getClient();
		TermsAggregationBuilder aggregation = AggregationBuilders.terms("cilentip_count").field("clientip");
		SearchResponse response = client.prepareSearch(getIndexName()).setTypes(getIndexType())
				.addAggregation(aggregation).execute().actionGet();
		Terms terms = response.getAggregations().get("cilentip_count");
		List<Terms.Bucket> buckets = (List<Terms.Bucket>) terms.getBuckets();
		int i = 1;
		for (Terms.Bucket bucket : buckets) {
			TomcatModel tomcat = new TomcatModel();
			if (isboolIp((String) bucket.getKey()) == true) {// 过滤掉不规则的ipv4
				tomcat.setClientip((String) bucket.getKey());
				tomcat.setCount((int) bucket.getDocCount());
				tomcat.setRowId(i);
				list.add(tomcat);
				if((i++)>10){
					break;
				}
			}
		}
		return list;
	}

	/**
	 * * 判断是否为合法IP * @return the ip
	 */
	public static boolean isboolIp(String ipAddress) {
		String ip = "([1-9]|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])(\\.(\\d|[1-9]\\d|1\\d{2}|2[0-4]\\d|25[0-5])){3}";
		Pattern pattern = Pattern.compile(ip);
		Matcher matcher = pattern.matcher(ipAddress);
		return matcher.matches();
	}

	@Override
	public TomcatModel tomcatRequestType(String type) {
		TransportClient client = getClient();
		TomcatModel tomcat = new TomcatModel();
		MatchQueryBuilder queryBuilder = QueryBuilders.matchQuery("verb", type);
		// GET POST ..
		SearchResponse response = client.prepareSearch(getIndexName()).setTypes(getIndexType())
				// .setQuery(QueryBuilders.termQuery("verb", requestType))
				// .setQuery(QueryBuilders.termQuery("verb", "GET"))
				// .setQuery(QueryBuilders.termQuery("clientip",
				// "192.168.206.1"))
				.setQuery(queryBuilder).execute().actionGet();
		SearchHits myhits = response.getHits();
		tomcat.addKey(type);
		tomcat.addValue(myhits.getTotalHits());

		return tomcat;
	}

	@Override
	public List<TomcatModel> tomcatRequestAll(Page page) {
		TransportClient client = getClient();
		List<TomcatModel> list = new ArrayList<TomcatModel>();
		SearchResponse response = client.prepareSearch(getIndexName()).setTypes(getIndexType()).setFrom(page.getStart())
				.setSize(page.getLength()).execute().actionGet();
		SearchHits myhits = response.getHits();
		page.setRecordsFiltered((int) myhits.getTotalHits());
		page.setRecordsTotal((int) myhits.getTotalHits());

		int i = 1;
		for (SearchHit hit : myhits.getHits()) {
			TomcatModel tomcat = new TomcatModel();
			Map<String, Object> map = hit.getSource();
			tomcat.setClientip((String) map.get("clientip"));
			tomcat.setResponse((String) map.get("response"));
			tomcat.setMessage((String) map.get("message"));
			tomcat.setType((String) map.get("verb"));
			tomcat.setTimestamp((String) map.get("timestamp"));
			tomcat.setRowId(i);
			list.add(tomcat);
			i++;
		}
		return list;
	}

	/**
	 * 错误日志统计
	 */
	@Override
	public List<TomcatModel> errorTomcatRequest(Page page) {
		TransportClient client = getClient();
		List<TomcatModel> list = new ArrayList<TomcatModel>();
		SearchResponse response = client.prepareSearch(getIndexName()).setTypes(getIndexType()).setFrom(page.getStart())
				.setSize(page.getLength()).setQuery(QueryBuilders.regexpQuery("response", "[3-5][0-9][0-9]")).execute().actionGet();
		SearchHits myhits = response.getHits();
		page.setRecordsFiltered((int) myhits.getTotalHits());
		page.setRecordsTotal((int) myhits.getTotalHits());

		int i = 1;
		for (SearchHit hit : myhits.getHits()) {
			TomcatModel tomcat = new TomcatModel();
			Map<String, Object> map = hit.getSource();
			tomcat.setClientip((String) map.get("clientip"));
			tomcat.setResponse((String) map.get("response"));
			tomcat.setMessage((String) map.get("message"));
			tomcat.setType((String) map.get("verb"));
			tomcat.setTimestamp((String) map.get("timestamp"));
			tomcat.setRowId(i);
			list.add(tomcat);
			i++;
		}
		return list;
	}
	
	@Override
	public TransportClient getClient() {
		return ESUtil.getClient();
	}

	@Override
	public String getIndexName() {
		return INDEX_NAME;
	}

	@Override
	public String getIndexType() {
		return INDEX_TYPE;
	}
}
