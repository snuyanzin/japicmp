package japicmp.maven;

import japicmp.config.Options;
import japicmp.output.xml.XmlOutput;
import japicmp.util.Optional;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;
import java.util.Locale;

@Mojo(name = "cmp-report", defaultPhase = LifecyclePhase.SITE)
public class JApiCmpReport extends AbstractMavenReport {
	@org.apache.maven.plugins.annotations.Parameter(required = false)
	private Version oldVersion;
	@org.apache.maven.plugins.annotations.Parameter(required = false)
	private List<DependencyDescriptor> oldVersions;
	@org.apache.maven.plugins.annotations.Parameter(required = false)
	private Version newVersion;
	@org.apache.maven.plugins.annotations.Parameter(required = false)
	private List<DependencyDescriptor> newVersions;
	@org.apache.maven.plugins.annotations.Parameter(required = false)
	private Parameter parameter;
	@org.apache.maven.plugins.annotations.Parameter(required = false)
	private List<Dependency> dependencies;
	@org.apache.maven.plugins.annotations.Parameter(required = false)
	private List<Dependency> oldClassPathDependencies;
	@org.apache.maven.plugins.annotations.Parameter(required = false)
	private List<Dependency> newClassPathDependencies;
	@org.apache.maven.plugins.annotations.Parameter(required = false)
	private boolean skip;
	@org.apache.maven.plugins.annotations.Parameter(required = true, readonly = true, property = "project.reporting.outputDirectory")
	private String outputDirectory;
	@Component
	private RepositorySystem repoSystem;
	@org.apache.maven.plugins.annotations.Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
	private RepositorySystemSession repoSession;
	@org.apache.maven.plugins.annotations.Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
	private List<RemoteRepository> remoteRepos;
	@org.apache.maven.plugins.annotations.Parameter(required = true, defaultValue = "${localRepository}")
	private ArtifactRepository localRepository;
	@org.apache.maven.plugins.annotations.Parameter(required = true, defaultValue = "${project.remoteArtifactRepositories}")
	private List<ArtifactRepository> artifactRepositories;
	@org.apache.maven.plugins.annotations.Parameter(required = true, defaultValue = "${project}")
	private MavenProject mavenProject;
	@org.apache.maven.plugins.annotations.Parameter(defaultValue = "${mojoExecution}", readonly = true)
	private MojoExecution mojoExecution;
	@org.apache.maven.plugins.annotations.Parameter(defaultValue = "(,${project.version})", readonly = true)
	private String versionRangeWithProjectVersion;
	private JApiCmpMojo mojo;
	private MavenParameters mavenParameters;
	private PluginParameters pluginParameters;

	@Override
	protected void executeReport(Locale locale) throws MavenReportException {
		try {
			JApiCmpMojo mojo = getMojo();
			if (this.skip || isPomModuleNeedingSkip()) {
				getLog().info("japicmp module set to skip");
				return;
			}
			Optional<XmlOutput> xmlOutputOptional = mojo.executeWithParameters(this.pluginParameters, this.mavenParameters);
			if (xmlOutputOptional.isPresent()) {
				XmlOutput xmlOutput = xmlOutputOptional.get();
				if (xmlOutput.getHtmlOutputStream().isPresent()) {
					ByteArrayOutputStream htmlOutputStream = xmlOutput.getHtmlOutputStream().get();
					String htmlString = htmlOutputStream.toString("UTF-8");
					htmlString = htmlString.replaceAll("</?html>", "");
					htmlString = htmlString.replaceAll("</?body>", "");
					htmlString = htmlString.replaceAll("</?head>", "");
					htmlString = htmlString.replaceAll("<title>[^<]*</title>", "");
					htmlString = htmlString.replaceAll("<META[^>]*>", "");
					Sink sink = getSink();
					sink.rawText(htmlString);
					sink.close();
				}
			}
		} catch (Exception e) {
			String msg = "Failed to generate report: " + e.getMessage();
			Sink sink = getSink();
			sink.text(msg);
			sink.close();
			throw new MavenReportException(msg, e);
		}
	}

	private JApiCmpMojo getMojo() {
		if (this.mojo != null) {
			return this.mojo;
		}
		this.mojo = new JApiCmpMojo();
		this.mavenParameters = new MavenParameters(this.artifactRepositories, this.localRepository,
				this.mavenProject, this.mojoExecution, this.versionRangeWithProjectVersion, this.repoSystem, this.repoSession,
				this.remoteRepos);
		this.pluginParameters = new PluginParameters(this.skip, this.newVersion, this.oldVersion, this.parameter, this.dependencies, Optional.<File>absent(), Optional.of(
				this.outputDirectory), false, this.oldVersions, this.newVersions, this.oldClassPathDependencies, this.newClassPathDependencies);
		return this.mojo;
	}

	private Options getOptions() {
		try {
			return getMojo().getOptions(this.pluginParameters, this.mavenParameters);
		} catch (MojoFailureException e) {
			getLog().debug("Failed to retrieve options: " + e.getLocalizedMessage(), e);
			return null;
		}
	}

	@Override
	public String getOutputName() {
		if (this.parameter != null && this.parameter.getReportLinkName() != null) {
			return this.parameter.getReportLinkName();
		}
		return "japicmp";
	}

	@Override
	public String getName(Locale locale) {
		if (this.parameter != null && this.parameter.getReportLinkName() != null) {
			return this.parameter.getReportLinkName();
		}
		return "japicmp";
	}

	@Override
	public String getDescription(Locale locale) {
		getMojo();
		if (this.skip || isPomModuleNeedingSkip()) {
			return "skipping report";
		}
		Options options = getOptions();
		if (options == null) {
			return "failed report";
		}
		return options.getDifferenceDescription();
	}

	private boolean isPomModuleNeedingSkip() {
		return this.pluginParameters.getParameterParam().getSkipPomModules()
			&& "pom".equalsIgnoreCase(this.mavenProject.getArtifact().getType());
	}
}
