package com.siddharth.stockticker.data.repository

import com.siddharth.stockticker.data.remote.dto.SearchQuoteDto

/**
 * A small on-device catalogue of liquid NSE names and the two headline indices.
 *
 * Two jobs:
 *  1. The search screen has content the instant it opens, before any typing or
 *     any network call — typing on a watch is the worst part of watch UX, so the
 *     fastest path to a symbol is one that needs no typing at all.
 *  2. Search still works offline.
 *
 * It holds symbols and company names only — never prices.
 */
object SymbolCatalog {

    data class Entry(val symbol: String, val name: String, val exchange: String = "NSE")

    val defaultWatchlist = listOf(
        "RELIANCE.NS",
        "TCS.NS",
        "HDFCBANK.NS",
        "INFY.NS",
        "SBIN.NS"
    )

    const val NIFTY = "^NSEI"
    const val SENSEX = "^BSESN"

    val entries: List<Entry> = listOf(
        Entry(NIFTY, "Nifty 50", "Index"),
        Entry(SENSEX, "BSE Sensex", "Index"),
        Entry("RELIANCE.NS", "Reliance Industries"),
        Entry("TCS.NS", "Tata Consultancy Services"),
        Entry("HDFCBANK.NS", "HDFC Bank"),
        Entry("ICICIBANK.NS", "ICICI Bank"),
        Entry("INFY.NS", "Infosys"),
        Entry("SBIN.NS", "State Bank of India"),
        Entry("BHARTIARTL.NS", "Bharti Airtel"),
        Entry("ITC.NS", "ITC"),
        Entry("LT.NS", "Larsen and Toubro"),
        Entry("HINDUNILVR.NS", "Hindustan Unilever"),
        Entry("AXISBANK.NS", "Axis Bank"),
        Entry("KOTAKBANK.NS", "Kotak Mahindra Bank"),
        Entry("BAJFINANCE.NS", "Bajaj Finance"),
        Entry("BAJAJFINSV.NS", "Bajaj Finserv"),
        Entry("MARUTI.NS", "Maruti Suzuki"),
        Entry("M&M.NS", "Mahindra and Mahindra"),
        Entry("TATAMOTORS.NS", "Tata Motors"),
        Entry("TATASTEEL.NS", "Tata Steel"),
        Entry("JSWSTEEL.NS", "JSW Steel"),
        Entry("HINDALCO.NS", "Hindalco Industries"),
        Entry("COALINDIA.NS", "Coal India"),
        Entry("NTPC.NS", "NTPC"),
        Entry("POWERGRID.NS", "Power Grid Corporation"),
        Entry("ONGC.NS", "Oil and Natural Gas Corp"),
        Entry("BPCL.NS", "Bharat Petroleum"),
        Entry("IOC.NS", "Indian Oil Corporation"),
        Entry("GAIL.NS", "GAIL India"),
        Entry("ADANIENT.NS", "Adani Enterprises"),
        Entry("ADANIPORTS.NS", "Adani Ports and SEZ"),
        Entry("ADANIGREEN.NS", "Adani Green Energy"),
        Entry("ADANIPOWER.NS", "Adani Power"),
        Entry("ASIANPAINT.NS", "Asian Paints"),
        Entry("NESTLEIND.NS", "Nestle India"),
        Entry("BRITANNIA.NS", "Britannia Industries"),
        Entry("TATACONSUM.NS", "Tata Consumer Products"),
        Entry("DABUR.NS", "Dabur India"),
        Entry("GODREJCP.NS", "Godrej Consumer Products"),
        Entry("TITAN.NS", "Titan Company"),
        Entry("DMART.NS", "Avenue Supermarts"),
        Entry("TRENT.NS", "Trent"),
        Entry("SUNPHARMA.NS", "Sun Pharmaceutical"),
        Entry("DRREDDY.NS", "Dr Reddys Laboratories"),
        Entry("CIPLA.NS", "Cipla"),
        Entry("DIVISLAB.NS", "Divis Laboratories"),
        Entry("APOLLOHOSP.NS", "Apollo Hospitals"),
        Entry("HCLTECH.NS", "HCL Technologies"),
        Entry("WIPRO.NS", "Wipro"),
        Entry("TECHM.NS", "Tech Mahindra"),
        Entry("LTIM.NS", "LTIMindtree"),
        Entry("PERSISTENT.NS", "Persistent Systems"),
        Entry("COFORGE.NS", "Coforge"),
        Entry("INDUSINDBK.NS", "IndusInd Bank"),
        Entry("BANKBARODA.NS", "Bank of Baroda"),
        Entry("PNB.NS", "Punjab National Bank"),
        Entry("CANBK.NS", "Canara Bank"),
        Entry("IDFCFIRSTB.NS", "IDFC First Bank"),
        Entry("FEDERALBNK.NS", "Federal Bank"),
        Entry("SBILIFE.NS", "SBI Life Insurance"),
        Entry("HDFCLIFE.NS", "HDFC Life Insurance"),
        Entry("ICICIGI.NS", "ICICI Lombard"),
        Entry("ICICIPRULI.NS", "ICICI Prudential Life"),
        Entry("LICI.NS", "Life Insurance Corp of India"),
        Entry("BAJAJ-AUTO.NS", "Bajaj Auto"),
        Entry("HEROMOTOCO.NS", "Hero MotoCorp"),
        Entry("EICHERMOT.NS", "Eicher Motors"),
        Entry("TVSMOTOR.NS", "TVS Motor Company"),
        Entry("ASHOKLEY.NS", "Ashok Leyland"),
        Entry("BOSCHLTD.NS", "Bosch"),
        Entry("ULTRACEMCO.NS", "UltraTech Cement"),
        Entry("GRASIM.NS", "Grasim Industries"),
        Entry("SHREECEM.NS", "Shree Cement"),
        Entry("AMBUJACEM.NS", "Ambuja Cements"),
        Entry("ACC.NS", "ACC"),
        Entry("VEDL.NS", "Vedanta"),
        Entry("SAIL.NS", "Steel Authority of India"),
        Entry("NMDC.NS", "NMDC"),
        Entry("JINDALSTEL.NS", "Jindal Steel and Power"),
        Entry("PIDILITIND.NS", "Pidilite Industries"),
        Entry("UPL.NS", "UPL"),
        Entry("SRF.NS", "SRF"),
        Entry("DEEPAKNTR.NS", "Deepak Nitrite"),
        Entry("TATAPOWER.NS", "Tata Power"),
        Entry("TORNTPOWER.NS", "Torrent Power"),
        Entry("SIEMENS.NS", "Siemens India"),
        Entry("ABB.NS", "ABB India"),
        Entry("BHEL.NS", "BHEL"),
        Entry("BEL.NS", "Bharat Electronics"),
        Entry("HAL.NS", "Hindustan Aeronautics"),
        Entry("IRCTC.NS", "IRCTC"),
        Entry("INDIGO.NS", "InterGlobe Aviation"),
        Entry("ZOMATO.NS", "Eternal (Zomato)"),
        Entry("NYKAA.NS", "FSN E-Commerce (Nykaa)"),
        Entry("PAYTM.NS", "One97 Communications"),
        Entry("POLICYBZR.NS", "PB Fintech"),
        Entry("DLF.NS", "DLF"),
        Entry("GODREJPROP.NS", "Godrej Properties"),
        Entry("OBEROIRLTY.NS", "Oberoi Realty"),
        Entry("JUBLFOOD.NS", "Jubilant FoodWorks"),
        Entry("MARICO.NS", "Marico"),
        Entry("COLPAL.NS", "Colgate-Palmolive India"),
        Entry("BERGEPAINT.NS", "Berger Paints"),
        Entry("HAVELLS.NS", "Havells India"),
        Entry("VOLTAS.NS", "Voltas"),
        Entry("CUMMINSIND.NS", "Cummins India"),
        Entry("MOTHERSON.NS", "Samvardhana Motherson"),
        Entry("BALKRISIND.NS", "Balkrishna Industries"),
        Entry("MRF.NS", "MRF"),
        Entry("PAGEIND.NS", "Page Industries"),
        Entry("MUTHOOTFIN.NS", "Muthoot Finance"),
        Entry("CHOLAFIN.NS", "Cholamandalam Investment"),
        Entry("SHRIRAMFIN.NS", "Shriram Finance"),
        Entry("RECLTD.NS", "REC"),
        Entry("PFC.NS", "Power Finance Corporation"),
        Entry("IRFC.NS", "Indian Railway Finance Corp"),
        Entry("HINDZINC.NS", "Hindustan Zinc"),
        Entry("LUPIN.NS", "Lupin"),
        Entry("AUROPHARMA.NS", "Aurobindo Pharma"),
        Entry("ZYDUSLIFE.NS", "Zydus Lifesciences"),
        Entry("ALKEM.NS", "Alkem Laboratories"),
        Entry("TORNTPHARM.NS", "Torrent Pharmaceuticals"),
        Entry("MAXHEALTH.NS", "Max Healthcare"),
        Entry("INDUSTOWER.NS", "Indus Towers"),
        Entry("IDEA.NS", "Vodafone Idea"),
        Entry("NAUKRI.NS", "Info Edge India"),
        Entry("MPHASIS.NS", "Mphasis"),
        Entry("OFSS.NS", "Oracle Financial Services"),
        Entry("TATAELXSI.NS", "Tata Elxsi"),
        Entry("KPITTECH.NS", "KPIT Technologies"),
        Entry("CGPOWER.NS", "CG Power and Industrial"),
        Entry("POLYCAB.NS", "Polycab India"),
        Entry("ASTRAL.NS", "Astral"),
        Entry("SUPREMEIND.NS", "Supreme Industries"),
        Entry("APLAPOLLO.NS", "APL Apollo Tubes"),
        Entry("JSWENERGY.NS", "JSW Energy"),
        Entry("NHPC.NS", "NHPC"),
        Entry("SJVN.NS", "SJVN"),
        Entry("IEX.NS", "Indian Energy Exchange"),
        Entry("BSE.NS", "BSE"),
        Entry("CDSL.NS", "Central Depository Services"),
        Entry("HDFCAMC.NS", "HDFC Asset Management"),
        Entry("NAM-INDIA.NS", "Nippon Life India AMC"),
        Entry("ABCAPITAL.NS", "Aditya Birla Capital"),
        Entry("LODHA.NS", "Macrotech Developers"),
        Entry("PRESTIGE.NS", "Prestige Estates"),
        Entry("PHOENIXLTD.NS", "Phoenix Mills"),
        Entry("IGL.NS", "Indraprastha Gas"),
        Entry("MGL.NS", "Mahanagar Gas"),
        Entry("PETRONET.NS", "Petronet LNG"),
        Entry("OIL.NS", "Oil India"),
        Entry("MANKIND.NS", "Mankind Pharma"),
        Entry("GLAND.NS", "Gland Pharma"),
        Entry("LAURUSLABS.NS", "Laurus Labs"),
        Entry("BIOCON.NS", "Biocon"),
        Entry("SYNGENE.NS", "Syngene International"),
        Entry("ESCORTS.NS", "Escorts Kubota"),
        Entry("EXIDEIND.NS", "Exide Industries"),
        Entry("SONACOMS.NS", "Sona BLW Precision"),
        Entry("BHARATFORG.NS", "Bharat Forge"),
        Entry("THERMAX.NS", "Thermax"),
        Entry("AIAENG.NS", "AIA Engineering"),
        Entry("SCHAEFFLER.NS", "Schaeffler India"),
        Entry("TIINDIA.NS", "Tube Investments of India"),
        Entry("RVNL.NS", "Rail Vikas Nigam"),
        Entry("IRB.NS", "IRB Infrastructure"),
        Entry("CONCOR.NS", "Container Corporation"),
        Entry("DELHIVERY.NS", "Delhivery"),
        Entry("BLUEDART.NS", "Blue Dart Express"),
        Entry("VBL.NS", "Varun Beverages"),
        Entry("UBL.NS", "United Breweries"),
        Entry("RADICO.NS", "Radico Khaitan"),
        Entry("MCDOWELL-N.NS", "United Spirits"),
        Entry("HONAUT.NS", "Honeywell Automation India"),
        Entry("3MINDIA.NS", "3M India"),
        Entry("CROMPTON.NS", "Crompton Greaves Consumer"),
        Entry("DIXON.NS", "Dixon Technologies"),
        Entry("AMBER.NS", "Amber Enterprises"),
        Entry("KAYNES.NS", "Kaynes Technology"),
        Entry("SUZLON.NS", "Suzlon Energy"),
        Entry("YESBANK.NS", "Yes Bank"),
        Entry("AUBANK.NS", "AU Small Finance Bank"),
        Entry("BANDHANBNK.NS", "Bandhan Bank"),
        Entry("UNIONBANK.NS", "Union Bank of India"),
        Entry("INDIANB.NS", "Indian Bank"),
        Entry("IOB.NS", "Indian Overseas Bank"),
        Entry("JIOFIN.NS", "Jio Financial Services"),
        Entry("HUDCO.NS", "HUDCO"),
        Entry("MAZDOCK.NS", "Mazagon Dock Shipbuilders"),
        Entry("COCHINSHIP.NS", "Cochin Shipyard"),
        Entry("BDL.NS", "Bharat Dynamics"),
        Entry("SOLARINDS.NS", "Solar Industries India")
    )

    private val bySymbol = entries.associateBy { it.symbol }

    fun nameFor(symbol: String): String? = bySymbol[symbol]?.name

    /**
     * Ranked local filter. Symbol-prefix matches come first because that is what
     * someone typing "REL" is almost always after.
     */
    fun filter(query: String, limit: Int = 24): List<Entry> {
        val q = query.trim().uppercase()
        if (q.isEmpty()) return entries.take(limit)
        return entries
            .mapNotNull { entry ->
                val sym = entry.symbol.substringBefore('.').uppercase()
                val name = entry.name.uppercase()
                val score = when {
                    sym == q -> 0
                    sym.startsWith(q) -> 1
                    name.startsWith(q) -> 2
                    name.split(' ').any { it.startsWith(q) } -> 3
                    sym.contains(q) -> 4
                    name.contains(q) -> 5
                    else -> return@mapNotNull null
                }
                entry to score
            }
            .sortedWith(compareBy({ it.second }, { it.first.name.length }))
            .take(limit)
            .map { it.first }
    }

    /** Folds a remote search hit into the same shape, keeping Indian listings. */
    fun fromRemote(dto: SearchQuoteDto): Entry? {
        val symbol = dto.symbol?.trim()?.uppercase() ?: return null
        if (!symbol.endsWith(".NS") && !symbol.endsWith(".BO") && !symbol.startsWith("^")) {
            return null
        }
        val name = dto.longName ?: dto.shortName ?: bySymbol[symbol]?.name ?: symbol
        val exchange = when {
            symbol.startsWith("^") -> "Index"
            symbol.endsWith(".BO") -> "BSE"
            else -> "NSE"
        }
        return Entry(symbol, name, exchange)
    }
}
