package spi;

import com.google.auto.service.AutoService;
import dagger.spi.BindingGraphPlugin;
import dagger.spi.DiagnosticReporter;
import dagger.model.BindingGraph;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.annotation.processing.Filer;

@AutoService(BindingGraphPlugin.class)
public final class StaticDependencyPlugin implements BindingGraphPlugin {
  private Filer filer;
  private Elements elements;
  private Types types;

  @Override
  public void initFiler(Filer filer) {
    this.filer = filer;
  }

  @Override
  public void initElements(Elements elements) {
    this.elements = elements;
  }

  @Override
  public void initTypes(Types types) {
    this.types = types;
  }

  @Override
  public void visitGraph(BindingGraph graph, DiagnosticReporter diagnosticReporter) {
    // Flag '-Adagger.fullBindingGraphValidation' must be set.
    // This is called twice, so second time through just ignore.
    if (!graph.isFullBindingGraph()) {
      return;
    }
  }
}
