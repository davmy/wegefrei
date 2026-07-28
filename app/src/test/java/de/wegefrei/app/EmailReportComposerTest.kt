package de.wegefrei.app

import android.content.Intent
import de.wegefrei.app.feature.photocapture.impl.ReportDetails
import de.wegefrei.app.feature.witness.impl.WitnessDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class EmailReportComposerTest {
    @Test
    fun `buildReportEmailBody matches the required template exactly`() {
        val witness =
            WitnessDetails(
                name = "Max Mustermann",
                address = "Musterstraße 1, 12345 Musterstadt",
                email = "max@example.com",
            )
        val report =
            ReportDetails(
                licensePlate = "KS-T 2394",
                make = "Ford",
                color = "Silber",
                address = "Musterplatz 5, 12345 Musterstadt",
                incidentDateTime = LocalDateTime.of(2011, 10, 6, 11, 27),
                violation = "Parken im absoluten Halteverbot und Radfahrstreifen, mehr als drei Minuten, kein Fahrzeughalter in der Nähe",
                obstruction = "Ich als Radfahrer muss auf die reguläre Fahrspur ausweichen",
                durationOver60Minutes = "Nein",
                photoUris = emptyList(),
            )

        val body = buildReportEmailBody(witness, report)

        val expected =
            """
            Sehr geehrte Damen und Herren,

            ich möchte folgende Verkehrsordnungswidrigkeit zur Anzeige bringen, mit der Bitte um Weiterverfolgung:

                Anzeigender = Zeuge: Max Mustermann, Musterstraße 1, 12345 Musterstadt, E-Mail-Adresse: max@example.com
                Weitere Zeugen: -
                Tatörtlichkeit: Musterplatz 5, 12345 Musterstadt
                Tatzeit / Zeit der Feststellung: 06.10.2011 11:27
                Angaben zum Fahrzeug, das falsch gestanden hat: KS-T 2394, Ford, Silber
                Angaben zum Verkehrsverstoß: Parken im absoluten Halteverbot und Radfahrstreifen, mehr als drei Minuten, kein Fahrzeughalter in der Nähe
                Angaben zu einer konkreten Verkehrsbehinderung oder -gefährdung: Ich als Radfahrer muss auf die reguläre Fahrspur ausweichen
                Standzeit länger als 60 Minuten: Nein

            Meine oben gemachten Angaben einschließlich meiner Personalien sind zutreffend und vollständig (§111 OWiG). Mir ist bewusst, dass ich als Zeuge zur wahrheitsgemäßen Aussage (§ 57 und § 161a StPO i. V. m. § 46 OWiG) und auch zu einem möglichen Erscheinen vor Gericht verpflichtet bin. Vorsätzlich falsche Angaben zu angeblichen Ordnungswidrigkeiten können eine Straftat (§ 164 StGB) darstellen.

            Mit freundlichen Grüßen
            Max Mustermann
            """.trimIndent()

        assertEquals(expected, body)
    }

    @Test
    fun `buildReportEmailBody omits the duration line when durationOver60Minutes is null`() {
        val witness =
            WitnessDetails(
                name = "Max Mustermann",
                address = "Musterstraße 1, 12345 Musterstadt",
                email = "max@example.com",
            )
        val report =
            ReportDetails(
                licensePlate = "KS-T 2394",
                make = "Ford",
                color = "Silber",
                address = "Musterplatz 5, 12345 Musterstadt",
                incidentDateTime = LocalDateTime.of(2011, 10, 6, 11, 27),
                violation = "Halten im absoluten Halteverbot",
                obstruction = "Ich als Radfahrer muss auf die reguläre Fahrspur ausweichen",
                durationOver60Minutes = null,
                photoUris = emptyList(),
            )

        val body = buildReportEmailBody(witness, report)

        val expected =
            """
            Sehr geehrte Damen und Herren,

            ich möchte folgende Verkehrsordnungswidrigkeit zur Anzeige bringen, mit der Bitte um Weiterverfolgung:

                Anzeigender = Zeuge: Max Mustermann, Musterstraße 1, 12345 Musterstadt, E-Mail-Adresse: max@example.com
                Weitere Zeugen: -
                Tatörtlichkeit: Musterplatz 5, 12345 Musterstadt
                Tatzeit / Zeit der Feststellung: 06.10.2011 11:27
                Angaben zum Fahrzeug, das falsch gestanden hat: KS-T 2394, Ford, Silber
                Angaben zum Verkehrsverstoß: Halten im absoluten Halteverbot
                Angaben zu einer konkreten Verkehrsbehinderung oder -gefährdung: Ich als Radfahrer muss auf die reguläre Fahrspur ausweichen

            Meine oben gemachten Angaben einschließlich meiner Personalien sind zutreffend und vollständig (§111 OWiG). Mir ist bewusst, dass ich als Zeuge zur wahrheitsgemäßen Aussage (§ 57 und § 161a StPO i. V. m. § 46 OWiG) und auch zu einem möglichen Erscheinen vor Gericht verpflichtet bin. Vorsätzlich falsche Angaben zu angeblichen Ordnungswidrigkeiten können eine Straftat (§ 164 StGB) darstellen.

            Mit freundlichen Grüßen
            Max Mustermann
            """.trimIndent()

        assertEquals(expected, body)
    }

    @Test
    fun `buildReportEmailBody omits the obstruction line when obstruction is null`() {
        val witness =
            WitnessDetails(
                name = "Max Mustermann",
                address = "Musterstraße 1, 12345 Musterstadt",
                email = "max@example.com",
            )
        val report =
            ReportDetails(
                licensePlate = "KS-T 2394",
                make = "Ford",
                color = "Silber",
                address = "Musterplatz 5, 12345 Musterstadt",
                incidentDateTime = LocalDateTime.of(2011, 10, 6, 11, 27),
                violation = "Parken im absoluten Halteverbot",
                obstruction = null,
                durationOver60Minutes = "Nein",
                photoUris = emptyList(),
            )

        val body = buildReportEmailBody(witness, report)

        val expected =
            """
            Sehr geehrte Damen und Herren,

            ich möchte folgende Verkehrsordnungswidrigkeit zur Anzeige bringen, mit der Bitte um Weiterverfolgung:

                Anzeigender = Zeuge: Max Mustermann, Musterstraße 1, 12345 Musterstadt, E-Mail-Adresse: max@example.com
                Weitere Zeugen: -
                Tatörtlichkeit: Musterplatz 5, 12345 Musterstadt
                Tatzeit / Zeit der Feststellung: 06.10.2011 11:27
                Angaben zum Fahrzeug, das falsch gestanden hat: KS-T 2394, Ford, Silber
                Angaben zum Verkehrsverstoß: Parken im absoluten Halteverbot
                Standzeit länger als 60 Minuten: Nein

            Meine oben gemachten Angaben einschließlich meiner Personalien sind zutreffend und vollständig (§111 OWiG). Mir ist bewusst, dass ich als Zeuge zur wahrheitsgemäßen Aussage (§ 57 und § 161a StPO i. V. m. § 46 OWiG) und auch zu einem möglichen Erscheinen vor Gericht verpflichtet bin. Vorsätzlich falsche Angaben zu angeblichen Ordnungswidrigkeiten können eine Straftat (§ 164 StGB) darstellen.

            Mit freundlichen Grüßen
            Max Mustermann
            """.trimIndent()

        assertEquals(expected, body)
    }

    @Test
    fun `buildReportEmailSubject returns a fixed subject`() {
        assertEquals("Anzeige einer Verkehrsordnungswidrigkeit", buildReportEmailSubject())
    }

    @Test
    fun `buildReportEmailIntent sets the recipient when an authority email is given`() {
        val intent = buildReportEmailIntent("Betreff", "Text", emptyList(), "ordnungsamt@example.com")

        assertEquals(listOf("ordnungsamt@example.com"), intent.getStringArrayExtra(Intent.EXTRA_EMAIL)?.toList())
    }

    @Test
    fun `buildReportEmailIntent omits the recipient when no authority email is given`() {
        val intent = buildReportEmailIntent("Betreff", "Text", emptyList(), null)

        assertNull(intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
    }

    @Test
    fun `buildReportEmailIntent omits the recipient when the authority email is blank`() {
        val intent = buildReportEmailIntent("Betreff", "Text", emptyList(), "   ")

        assertNull(intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
    }
}
