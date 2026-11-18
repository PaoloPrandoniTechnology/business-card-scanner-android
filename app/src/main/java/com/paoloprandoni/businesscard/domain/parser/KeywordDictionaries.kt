package com.paoloprandoni.businesscard.domain.parser

/**
 * Dizionari di parole chiave (italiano + inglese) usati dal parser euristico.
 *
 * I confronti avvengono per parola intera e senza distinzione tra maiuscole/minuscole.
 */
internal object KeywordDictionaries {

    /** Ruoli / qualifiche professionali. */
    val ROLE_KEYWORDS: Set<String> = setOf(
        // C-level e ruoli apicali
        "ceo", "cfo", "coo", "cto", "cio", "cmo", "cqo", "cpo",
        "chief", "executive", "officer", "managing director", "general manager",
        "amministratore delegato", "amministratore unico", "amministratore",
        "presidente", "vicepresidente", "vice presidente", "president", "vice president",
        "board member", "consigliere", "socio", "socio fondatore", "fondatore", "founder",
        "co-founder", "cofounder", "owner", "titolare", "imprenditore", "entrepreneur",
        "director", "direttore", "dirigente", "head of", "head", "capo", "responsabile",
        "coordinator", "coordinatore", "supervisor", "supervisore", "manager",
        "project manager", "product manager", "account manager", "sales manager",
        "export manager", "area manager", "brand manager", "office manager",
        "partner", "associate", "associato", "advisor", "consultant", "consulente",
        // Vendite / marketing
        "sales", "commerciale", "vendite", "marketing", "communication", "comunicazione",
        "agente", "rappresentante", "buyer", "purchasing", "acquisti", "operations",
        "business development", "account", "customer", "customer care", "support",
        // Tecnico / IT
        "engineer", "ingegnere", "developer", "sviluppatore", "programmatore",
        "software", "hardware", "architect", "architetto", "designer", "graphic designer",
        "ux", "ui", "data scientist", "analyst", "analista", "controller", "auditor",
        "revisore", "specialist", "specialista", "tecnico", "technician", "it manager",
        // Legale / amministrativo
        "avvocato", "lawyer", "attorney", "notaio", "commercialista", "ragioniere",
        "contabile", "accountant", "segreteria", "segretario", "assistente", "assistant",
        "receptionist", "impiegato", "addetto", "referente", "delegato", "procuratore",
        "funzionario", "operatore", "back office", "front office",
        // Altro
        "medico", "dottore", "infermiere", "farmacista", "psicologo", "biologo", "chimico",
        "fisico", "ricercatore", "professore", "docente", "insegnante", "giornalista",
        "fotografo", "traduttore", "interprete", "hostess", "steward", "geometra",
        "stage", "intern", "tirocinante", "apprendista", "freelance", "retired",
        "president", "treasurer", "secretary",
    )

    /** Suffissi/marchi societari che identificano con forza una ragione sociale. */
    val COMPANY_STRONG_MARKERS: Set<String> = setOf(
        "srl", "s.r.l.", "s.r.l", "srls", "s.p.a.", "s.p.a", "spa", "snc", "s.n.c.",
        "sas", "s.a.s.", "scrl", "scarl", "societa", "società", "cooperativa", "coop",
        "gmbh", "mbh", "ag", "sarl", "s.a.r.l.", "ltd", "limited", "llc", "inc",
        "incorporated", "corp", "corporation", "plc", "pty", "b.v.", "bv", "n.v.",
        "ab", "oy", "aps", "kg", "ug", "sa", "s.a.", "spa.", "srl.",
        "& figli", "e figli", "f.lli", "fratelli", "ditta", "impresa", "azienda",
    )

    /** Parole che suggeriscono un'azienda/studio senza essere marchi societari. */
    val COMPANY_GENERIC_MARKERS: Set<String> = setOf(
        "group", "gruppo", "holding", "studio", "studi", "associati", "associates",
        "partners", "consulting", "consulenza", "solutions", "soluzioni", "services",
        "servizi", "systems", "sistemi", "technologies", "tecnologie", "digital",
        "agency", "agenzia", "media", "engineering", "industries", "industrial",
        "company", "enterprise", "international", "global", "academy", "lab", "labs",
        "software", "network", "networks", "logistics", "immobiliare", "costruzioni",
        "studio legale", "studio tecnico", "studio commerciale", "farmacia", "clinica",
    )

    /** Parole chiave che iniziano un indirizzo stradale. */
    val STREET_KEYWORDS: Set<String> = setOf(
        "via", "viale", "v.le", "piazza", "p.zza", "p.za", "piazzale", "piazzetta",
        "corso", "largo", "vicolo", "strada", "stradone", "lungomare", "contrada",
        "traversa", "salita", "borgo", "localita", "località", "frazione", "zona",
        "road", "street", "avenue", "ave", "boulevard", "blvd", "square", "lane",
        "drive", "plaza", "rue", "calle", "strasse", "straße", "weg", "place",
        "p.o. box", "po box", "casella postale",
    )

    /** Parole che interrompono la raccolta di un indirizzo multi-riga. */
    val ADDRESS_STOP_WORDS: Set<String> = setOf(
        "tel", "telefono", "phone", "fax", "cell", "cellulare", "mobile", "email",
        "e-mail", "mail", "web", "sito", "www", "p.iva", "piva", "partita iva",
        "c.f.", "cf", "codice fiscale", "vat", "rea", "sdi", "pec", "iban",
        "ruolo", "title", "posizione",
    )

    /**
     * Identificativi fiscali/amministrativi: le loro cifre non devono mai essere
     * interpretate come numeri di telefono.
     */
    val FISCAL_KEYWORDS: Set<String> = setOf(
        "p.iva", "piva", "p. iva", "partita iva", "vat", "vat number", "c.f.", "c.f",
        "cf", "codice fiscale", "tax code", "rea", "sdi", "pec", "iban",
        "codice destinatario", "registro imprese",
    )

    /** Parole chiave che indicano un numero di fax. */
    val FAX_WORDS: List<String> = listOf("fax", "telefax", "fax:")

    /** Parole chiave che indicano un numero mobile. */
    val MOBILE_WORDS: List<String> = listOf(
        "cell", "cellulare", "mobile", "mob", "portatile", "portable", "whatsapp",
        "whats", "gsm", "personale", "privato", "private", "cellular",
    )

    /** Parole chiave che indicano un numero fisso / ufficio. */
    val WORK_WORDS: List<String> = listOf(
        "tel", "telefono", "phone", "ph", "uff", "ufficio", "office", "work",
        "lavoro", "sede", "centralino", "switchboard", "studio", "direct", "diretto",
        "fisso", "landline",
    )

    /** Nomi propri più comuni (usati per capire l'ordine nome/cognome). */
    val FIRST_NAMES: Set<String> = setOf(
        // Italiano
        "alessandro", "alessandra", "alberto", "alberto", "aldo", "alessio", "alice",
        "amalia", "andrea", "angelo", "anna", "annalisa", "antonio", "antonella",
        "arianna", "barbara", "beatrice", "benedetta", "bianca", "bruno", "carlo",
        "carmela", "caterina", "cesare", "chiara", "christian", "cinzia", "claudio",
        "claudia", "cristian", "cristina", "daniela", "daniele", "davide", "debora",
        "diego", "domenico", "edoardo", "elena", "eleonora", "elia", "elisa",
        "elisabetta", "emanuele", "emilio", "enrico", "erica", "ernesto", "ettore",
        "fabio", "fabrizio", "federica", "federico", "filippo", "flavia", "francesca",
        "francesco", "gabriele", "gabriella", "gaetano", "gennaro", "giacomo",
        "gianluca", "gianni", "gigliola", "giorgio", "giorgia", "giovanna", "giovanni",
        "giulia", "giulio", "giuseppe", "giuseppina", "gloria", "guido", "ilaria",
        "irene", "ivan", "jacopo", "laura", "leonardo", "letizia", "lidia", "lorenzo",
        "lorenza", "luca", "lucia", "luciano", "luigi", "luisa", "manuela", "marco",
        "marcello", "margherita", "maria", "marina", "mario", "marta", "martina",
        "massimo", "massimiliano", "matteo", "maurizio", "michele", "michela", "monica",
        "moreno", "nicola", "nicolo", "nicolò", "nadia", "oriana", "paola", "paolo",
        "patrizia", "piero", "pietro", "raffaele", "raffaella", "renato", "riccardo",
        "roberta", "roberto", "romano", "rosa", "rosanna", "salvatore", "samuele",
        "sandra", "sara", "sergio", "serena", "silvia", "silvio", "simona", "simone",
        "sonia", "stefania", "stefano", "susanna", "tatiana", "teresa", "tommaso",
        "valentina", "valerio", "vanessa", "vincenzo", "virginia", "vittorio",
        // Inglese / internazionale
        "adam", "alex", "alexander", "amanda", "amy", "andrew", "angela", "anthony",
        "barbara", "benjamin", "brian", "caroline", "catherine", "charles", "chris",
        "christine", "christopher", "daniel", "david", "deborah", "donald", "edward",
        "elizabeth", "emily", "emma", "eric", "george", "helen", "henry", "james",
        "jane", "jason", "jennifer", "jessica", "john", "jonathan", "joseph", "julia",
        "karen", "kevin", "laura", "linda", "lisa", "mark", "martin", "mary",
        "matthew", "michael", "michelle", "nancy", "nicholas", "oliver", "pamela",
        "patrick", "paul", "peter", "rachel", "richard", "robert", "sandra", "sarah",
        "stephen", "steven", "susan", "thomas", "timothy", "victoria", "william",
    )

    private val HONORIFIC_CANONICAL: Map<String, String> = mapOf(
        "ing" to "Ing.",
        "dott" to "Dott.",
        "dott.ssa" to "Dott.ssa",
        "dottoressa" to "Dott.ssa",
        "avv" to "Avv.",
        "arch" to "Arch.",
        "prof" to "Prof.",
        "rag" to "Rag.",
        "geom" to "Geom.",
        "sig" to "Sig.",
        "sig.ra" to "Sig.ra",
        "dr" to "Dr.",
        "mr" to "Mr.",
        "mrs" to "Mrs.",
        "ms" to "Ms.",
        "eng" to "Eng.",
        "notaio" to "Notaio",
    )

    /** Normalizza un titolo ("ing." → "Ing.", "dott.ssa" → "Dott.ssa"). */
    fun canonicalHonorific(raw: String): String {
        val key = raw.trim().trimEnd('.').lowercase()
        return HONORIFIC_CANONICAL[key] ?: HONORIFIC_CANONICAL[raw.trim().lowercase()] ?: raw.trim()
    }

    /** Top level domain accettati per riconoscere un sito web. */
    val TLDS: Set<String> = setOf(
        "com", "it", "net", "org", "eu", "de", "fr", "uk", "co", "info", "biz",
        "io", "ai", "app", "dev", "shop", "store", "online", "site", "tech", "cloud",
        "studio", "agency", "group", "consulting", "design", "art", "me", "tv", "us",
        "ca", "ch", "at", "es", "nl", "be", "se", "no", "dk", "fi", "pl", "pt", "gr",
        "ie", "cz", "hu", "ro", "bg", "hr", "si", "sk", "lt", "lv", "ee", "ru", "ua",
        "tr", "il", "in", "cn", "jp", "kr", "sg", "hk", "tw", "au", "nz", "br", "mx",
        "ar", "cl", "za", "ae", "sa", "eg", "ma", "li", "mc", "sm", "va", "email",
        "digital", "solutions", "services", "systems", "network", "team", "care",
    )

    val COUNTRY_NAMES: Set<String> = setOf(
        "italia", "italy", "svizzera", "switzerland", "francia", "france", "germania",
        "germany", "deutschland", "spagna", "spain", "austria", "belgio", "belgium",
        "olanda", "netherlands", "regno unito", "united kingdom", "gran bretagna",
        "usa", "united states", "stati uniti", "san marino", "monaco", "slovenia",
        "croazia", "croatia", "grecia", "greece", "portogallo", "portugal", "polonia",
        "poland", "romania", "ungheria", "hungary", "ceca", "czech republic",
        "slovacchia", "slovakia", "svezia", "sweden", "norvegia", "norway",
        "danimarca", "denmark", "finlandia", "finland", "irlanda", "ireland",
        "lussemburgo", "luxembourg", "malta", "cipro", "cyprus", "bulgaria", "estonia",
        "lettonia", "latvia", "lituania", "lithuania", "albania", "serbia", "bosnia",
        "montenegro", "macedonia", "moldavia", "ucraina", "ukraine", "russia",
        "turchia", "turkey", "israele", "israel", "egitto", "egypt", "marocco",
        "morocco", "tunisia", "brasile", "brazil", "argentina", "messico", "mexico",
        "canada", "australia", "giappone", "japan", "cina", "china", "india",
        "emirati arabi uniti", "uae",
    )
}
