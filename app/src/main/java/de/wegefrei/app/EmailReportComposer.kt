package de.wegefrei.app

import android.content.Intent
import android.net.Uri
import de.wegefrei.app.feature.photocapture.impl.ReportDetails
import de.wegefrei.app.feature.witness.impl.WitnessDetails
import java.time.format.DateTimeFormatter
import java.util.Locale

private val REPORT_DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY)

fun buildReportEmailSubject(): String = "Anzeige einer Verkehrsordnungswidrigkeit"

fun buildReportEmailBody(witness: WitnessDetails, report: ReportDetails): String {
    val incidentDateTime = report.incidentDateTime.format(REPORT_DATE_TIME_FORMATTER)
    return """
        Sehr geehrte Damen und Herren,

        ich möchte folgende Verkehrsordnungswidrigkeit zur Anzeige bringen, mit der Bitte um Weiterverfolgung:

            Anzeigender = Zeuge: ${witness.name}, ${witness.address}, E-Mail-Adresse: ${witness.email}
            Weitere Zeugen: -
            Tatörtlichkeit: ${report.address}
            Tatzeit(en)/Zeit der Feststellung: $incidentDateTime
            Angaben zum Fahrzeug, das falsch gestanden hat: ${report.licensePlate}, ${report.make}, ${report.color}, PKW
            Angaben zum Verkehrsverstoß: ${report.violation}
            Angaben zu einer konkreten Verkehrsbehinderung oder -gefährdung: ${report.obstruction}
            Standzeit länger als 60 Minuten: ${report.durationOver60Minutes}

        Meine oben gemachten Angaben einschließlich meiner Personalien sind zutreffend und vollständig (§111 OWiG). Mir ist bewusst, dass ich als Zeuge zur wahrheitsgemäßen Aussage (§ 57 und § 161a StPO i. V. m. § 46 OWiG) und auch zu einem möglichen Erscheinen vor Gericht verpflichtet bin. Vorsätzlich falsche Angaben zu angeblichen Ordnungswidrigkeiten können eine Straftat (§ 164 StGB) darstellen.

        Mit freundlichen Grüßen
        ${witness.name}
    """.trimIndent()
}

fun buildReportEmailIntent(subject: String, body: String, attachmentUris: List<Uri>): Intent {
    val action = if (attachmentUris.isEmpty()) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
    return Intent(action).apply {
        type = "message/rfc822"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        if (attachmentUris.isNotEmpty()) {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(attachmentUris))
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
