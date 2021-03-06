package molmed.report

import java.io.File
import java.util.jar.JarFile
import molmed.config.FileVersionUtilities._
import molmed.config.Constants
import java.io.BufferedReader
import java.io.PrintWriter
import molmed.utils.Resources


/**
 * Generate reports from Piper, containing information on which Piper version
 * was used, which resources were used, etc.
 */
object ReportGenerator {

  private def writeReport(template: String, outputFile: File): File = {
    val writer = new PrintWriter(outputFile)
    writer.write(template)
    writer.close()
    outputFile
  }

  private def getJarsOnClassPath(): Array[File] = {
    val classPath = System.getProperty("java.class.path").split(":")
    val jars = classPath.
      map(x => new File(x))
    jars
  }

  /**
   * Parses the class path searching for the piper jar and checks it's version
   * from the manifest. Will fail misserably if there are multiple jar files
   * on the classpath where the file name start with "piper_".
   *
   * This will only work once everything has been packed into jar-files,
   * so it will return Unknown when testing e.g. in eclipse.
   *
   * @return the current piper version
   */
  private def getPiperVersion(): String = {
    val jars = getJarsOnClassPath()
    val pipersJars = jars.filter(s => s.getName().startsWith("piper_")).toSeq

    if (pipersJars.size == 1) {
      val jarFile = new JarFile(pipersJars(0).getAbsolutePath())

      val manifest = jarFile.getManifest()
      val attributes = manifest.getMainAttributes()
      val version = attributes.getValue("Implementation-Version")

      version
    } else
      Constants.unknown

  }

  /**
   * Will attempt to load the GATK version from the GATK jar on the
   * class path. Looks to the string:
   * "org.broadinstitute.gatk.engine.CommandLineGATK.version" in
   * the "GATKText.properties" file and used that as the version of GATK.
   */
  private def getGATKVersion(): String = {
    val jars = getJarsOnClassPath()
    val gatkJars =
      jars.filter(s => s.getName().contains("GenomeAnalysisTK.jar"))

    if (gatkJars.size == 1) {
      val jarFile = new JarFile(gatkJars(0).getAbsolutePath())

      val properties = jarFile.getEntry("GATKText.properties")
      val version =
        if (properties == null)
          Constants.unknown
        else {
          val inputStream = jarFile.getInputStream(properties)

          val reader = scala.io.Source.fromInputStream(inputStream)
          val versionLine =
            reader.getLines.find(p =>
              p.startsWith("org.broadinstitute.gatk.engine.CommandLineGATK.version="))
          val versionFromFile = versionLine.getOrElse(Constants.unknown).split("=")(1)

          reader.close
          inputStream.close()

          versionFromFile
        }
      version
    } else
      Constants.unknown
  }

  /**
   * Construct the report for the RNACounts
   * qscript and write it to file.
   * @param file File to write output to.
   * @param reference The reference file used.
   * @param transcripts The transcripts used
   * @param maskFile	The mask file used
   * @param resourceMap A map of resources like the one generated by
   *                    FileAndProgramResourceConfig.configureResourcesFromConfigXML
   * @return the file that was created and written to.
   */
  def constructRNACountsReport(
    resourceMap: ResourceMap,
    reference: File,
    transcripts: File,
    maskFile: Option[File],
    outputFile: File): File = {

    val piperVersion = getPiperVersion()
    val tophatVersion = fileVersionFromKey(resourceMap, Constants.TOPHAP)
    val samtoolsVersion = fileVersionFromKey(resourceMap, Constants.SAMTOOLS)
    val rnaQCVersion = fileVersionFromKey(resourceMap, Constants.RNA_SEQC)

    val referenceName = reference.getName()

    val template =
      s"""
******
README
******
      
Data has been mapped to the reference using Tophat. Transcripts were quantified using cufflinks, and quality control data was collected using RNA-SeQC. The pipeline system used was Piper (see below for more information).      

The versions of programs and references used:
piper: $piperVersion
samtools: $samtoolsVersion
tophat: $tophatVersion
RNA-SeQC: $rnaQCVersion

reference: $referenceName
transcript reference: ${transcripts.getAbsolutePath()}
${if (maskFile.isDefined) "mask file (masking e.g. rRNAs):" + maskFile.get.getAbsolutePath() else ""} 


piper
-----
Piper is a pipeline system developed and maintained at the National Genomics Infrastructure build on top of GATK Queue. For more information and the source code visit: www.github.com/NationalGenomicsInfrastructure/piper
      """

    writeReport(template, outputFile)
  }

  /**
   * Construct the report for the DNABestPracticeVariantCallingReport
   * qscript and write it to file.
   * @param file File to write output to.
   * @param reference The reference file used.
   * @param resourceMap A map of resources like the one generated by
   *                    FileAndProgramResourceConfig.configureResourcesFromConfigXML
   * @return the file that was created and written to.
   */
  def constructDNABestPracticeVariantCallingReport(
    resourceMap: ResourceMap,
    reference: File,
    outputFile: File): File = {

    val piperVersion = getPiperVersion()
    val bwaVersion = fileVersionFromKey(resourceMap, Constants.BWA)
    val samtoolsVersion = fileVersionFromKey(resourceMap, Constants.SAMTOOLS)
    val qualimapVersion = fileVersionFromKey(resourceMap, Constants.QUALIMAP)
    val gatkVersion = getGATKVersion()
    val snpEffVersion = fileVersionFromKey(resourceMap, Constants.SNP_EFF)
    val snpEffReference = fileVersionFromKey(resourceMap, Constants.SNP_EFF_REFERENCE)
    val referenceName = reference.getName()
    val dbSNPVersion = fileVersionFromKey(resourceMap, Constants.DB_SNP)
    val thousandGenomesIndelsVersion = fileVersionFromKey(resourceMap, Constants.THOUSAND_GENOMES)
    val millsVersion = fileVersionFromKey(resourceMap, Constants.MILLS)
    val hapmap = fileVersionFromKey(resourceMap, Constants.HAPMAP)
    val omni = fileVersionFromKey(resourceMap, Constants.OMNI)
    val indelsString =
      multipleFileVersionsFromKey(resourceMap, Constants.INDELS).
        map(x =>
          "indel resource file: {" + x.file.getName() + " version: " + x.version.getOrElse(Constants.unknown) + "}").
        mkString("\n")

    val template =
      s"""
******
README
******

Data has been aligned to the reference using bwa. The raw alignments have then been deduplicated, recalibrated and indel realigned using GATK. For deduplication PicardMarkDuplicates, available in the Picard version that is bundled with the current version of GATK, has been used. Quality control information was gathered using Qualimap. SNVs and indels have been called using the GATK HaplotypeCaller. These variants were then functionally annotated using snpEff. The pipeline used was Piper, see below for more information.

The versions of programs and references used:
piper: $piperVersion
bwa: $bwaVersion
samtools: $samtoolsVersion
qualimap: $qualimapVersion
snpEff: $snpEffVersion
snpEff reference: $snpEffReference
gatk: $gatkVersion

reference: $referenceName
db_snp: $dbSNPVersion
hapmap: $hapmap
omni: $omni
1000G_indels: $thousandGenomesIndelsVersion
Mills_and_1000G_golden_standard_indels: $millsVersion

$indelsString

piper
-----
Piper is a pipeline system developed and maintained at the National Genomics Infrastructure build on top of GATK Queue. For more information and the source code visit: www.github.com/NationalGenomicsInfrastructure/piper
      """

    writeReport(template, outputFile)
  }

  
    /**
   * Construct the report for the Haloplex qscript and write it to file.
   * @param file File to write output to.
   * @param reference The reference file used.
   * @param resourceMap A map of resources like the one generated by
   *                    FileAndProgramResourceConfig.configureResourcesFromConfigXML
   * @param resources The Resources instance used by the Haloplex script
   * @return the file that was created and written to.
   */
  def constructHaloplexReport(
    resourceMap: ResourceMap,
    resources: Resources,
    reference: File,
    outputFile: File): File = {

    val piperVersion = getPiperVersion()
    val cutadaptVersion = fileVersionFromKey(resourceMap, Constants.CUTADAPT)
    val bwaVersion = fileVersionFromKey(resourceMap, Constants.BWA)
    val samtoolsVersion = fileVersionFromKey(resourceMap, Constants.SAMTOOLS)
    val gatkVersion = getGATKVersion()

    val referenceName = reference.getName()

    val template =
      s"""
******
README
******

The raw fastq reads have been adaptor trimmed using cutadapt. They were then aligned to to the reference using bwa aln. 
      
The raw alignments have then been recalibrated and cleaned using GATK. Furthermore the reads have been clipped by 5 bases at the 5'-end according to Agilents recommendations. Finally SNVs and indels have been called using the UnifiedGenotyper. The pipeline used was Piper, see below for more information.

The versions of programs and references used:
piper: $piperVersion
cutadapt: $cutadaptVersion
bwa: $bwaVersion
samtools: $samtoolsVersion
gatk: $gatkVersion

reference: $referenceName
db_snp: ${resources.dbsnp}
hapmap: ${resources.hapmap}
omni: ${resources.omni}
1000G_indels: ${resources.phase1}
Mills_and_1000G_golden_standard_indels: ${resources.mills}

piper
-----
Piper is a pipeline system developed and maintained at the National Genomics Infrastructure build on top of GATK Queue. For more information and the source code visit: www.github.com/NationalGenomicsInfrastructure/piper
      """

    writeReport(template, outputFile)
  }
  
}
