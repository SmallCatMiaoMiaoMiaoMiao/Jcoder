package org.nlpcn.jcoder.run.mvc.processor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

import org.nlpcn.jcoder.domain.Task;
import org.nlpcn.jcoder.run.annotation.Cache;
import org.nlpcn.jcoder.run.java.JavaRunner;
import org.nlpcn.jcoder.run.mvc.cache.CacheEntry;
import org.nlpcn.jcoder.scheduler.ThreadManager;
import org.nlpcn.jcoder.util.DateUtils;
import org.nutz.lang.Lang;
import org.nutz.mvc.ActionContext;
import org.nutz.mvc.ActionInfo;
import org.nutz.mvc.NutConfig;
import org.nutz.mvc.impl.processor.AbstractProcessor;

public class ApiMethodInvokeProcessor extends AbstractProcessor {

	private AtomicLong al = new AtomicLong();

	private Cache cache;

	private CacheEntry cacheEntry;

	@Override
	public void init(NutConfig config, ActionInfo ai) throws Throwable {
		super.init(config, ai);
		cache = ai.getMethod().getAnnotation(Cache.class);
	}

	public void process(ActionContext ac) throws Throwable {
		String threadName = null;
		Task module = (Task) ac.getModule();
		Method method = ac.getMethod();
		Object[] args = ac.getMethodArgs();
		try {
			threadName = module.getName() + "@" + ac.getRequest().getRemoteAddr() + "@" + DateUtils.formatDate(new Date(), "yyyyMMddHHmmss") + "@" + al.getAndIncrement();
			ThreadManager.add2ActionTask(threadName, Thread.currentThread());
			Object result = null;
			if (cache == null) {
				result = new JavaRunner(module).compile().instance().execute(method, args);
			} else {
				result = getCache(module, method).execute(args);
				if (result == CacheEntry.NULL) {
					result = null;
				}
			}
			ac.setMethodReturn(result);
			doNext(ac);
		} catch (IllegalAccessException e) {
			throw Lang.unwrapThrow(e);
		} catch (IllegalArgumentException e) {
			throw Lang.unwrapThrow(e);
		} catch (InvocationTargetException e) {
			throw e.getCause();
		} finally {
			ThreadManager.removeActionIfOver(threadName);
		}
	}

	private CacheEntry getCache(Task module, Method method) {

		if (cacheEntry == null) {
			synchronized (al) {
				if (cacheEntry == null) {
					cacheEntry = new CacheEntry(module, method, cache.time(), cache.size(), cache.block());
				}
			}
		}

		return cacheEntry;
	}

}