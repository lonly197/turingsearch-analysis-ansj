package cn.com.turingsearch.plugin;

import org.elasticsearch.action.ActionModule;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.common.inject.multibindings.Multibinder;
import org.elasticsearch.index.analysis.AnalysisModule;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.rest.RestModule;
import org.elasticsearch.rest.action.cat.AbstractCatAction;

import java.util.Collection;
import java.util.Collections;

import cn.com.turingsearch.action.AnsjAction;
import cn.com.turingsearch.action.TransportAnsjAction;
import cn.com.turingsearch.cat.AnalyzerCatAction;
import cn.com.turingsearch.cat.AnsjCatAction;
import cn.com.turingsearch.index.analysis.AnsjAnalysis;
import cn.com.turingsearch.index.analysis.AnsjAnalysisBinderProcessor;
import cn.com.turingsearch.rest.RestAnsjAction;

public class AnalysisAnsjPlugin extends Plugin {

	@Override
	public String name() {
		return "analysis-ansj";
	}

	@Override
	public String description() {
		return "ansj analysis";
	}

	@Override
	public Collection<Module> nodeModules() {
		return Collections.<Module> singletonList(new AnsjModule());
	}

	public void onModule(ActionModule actionModule) {
		actionModule.registerAction(AnsjAction.INSTANCE, TransportAnsjAction.class);
	}

	public void onModule(AnalysisModule model) {
		model.addProcessor(new AnsjAnalysisBinderProcessor());
	}

	public void onModule(RestModule restModule) {
		restModule.addRestAction(RestAnsjAction.class);
	}

	public static class AnsjModule extends AbstractModule {
		@Override
		protected void configure() {
			bind(AnsjAnalysis.class).asEagerSingleton();
			Multibinder<AbstractCatAction> catActionMultibinder = Multibinder.newSetBinder(binder(), AbstractCatAction.class);
			catActionMultibinder.addBinding().to(AnalyzerCatAction.class).asEagerSingleton();
			catActionMultibinder.addBinding().to(AnsjCatAction.class).asEagerSingleton();
		}
	}
}
