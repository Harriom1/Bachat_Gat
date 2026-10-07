/**
 * Bachat Gat - Location-based Bank & IFSC Directory
 * Provides automated bank branch and IFSC lookup based on location (District/Taluka/City),
 * token & prefix matching (e.g. 'Ahilyanaga' -> 'Ahilyanagar'), manual entry fallback ('OTHER'),
 * and reverse IFSC verification.
 */
const BankDirectory = {
  // Aliases mapping talukas, former names, and short names to district keys
  locationAliases: {
    'ahilyanagar': [
      'ahmednagar', 'nagar', 'ahilya', 'ahilyanaga', 'sangamner', 'shrirampur',
      'rahuri', 'kopargaon', 'nevasa', 'shevgaon', 'pathardi', 'parner',
      'karjat', 'jamkhed', 'shrigonda', 'akole', 'rahata', 'shirdi'
    ],
    'pune': [
      'poona', 'haveli', 'baramati', 'hadapsar', 'kothrud', 'shirur', 'junnar',
      'khed', 'ambegaon', 'maval', 'mulshi', 'daund', 'indapur', 'bhor',
      'purandar', 'velhe', 'koregaon', 'pimpri', 'chinchwad', 'wakad', 'baner'
    ],
    'chhatrapati sambhajinagar': [
      'aurangabad', 'sambhajinagar', 'chhatrapati', 'paithan', 'gangapur',
      'vaijapur', 'kannad', 'khuldabad', 'sillod', 'soygaon', 'phulambri'
    ],
    'nashik': [
      'nasik', 'malegaon', 'sinnar', 'niphad', 'yeola', 'igatpuri',
      'dindori', 'baglan', 'kalwan', 'chandwad', 'deola', 'trimbak', 'trimbakeshwar'
    ],
    'satara': [
      'karad', 'wai', 'phaltan', 'mahabaleshwar', 'khatav', 'man',
      'patan', 'jaoli', 'khandala', 'koregaon satara'
    ],
    'solapur': [
      'sholapur', 'pandharpur', 'barshi', 'mohol', 'madha', 'karmala',
      'sangola', 'mangalwedha', 'malshiras', 'akkalkot'
    ],
    'kolhapur': [
      'karveer', 'ichalkaranji', 'hatkanangale', 'shirol', 'kagal',
      'gadhinglaj', 'radhanagari', 'panhala', 'shahuwadi', 'bhudargad', 'ajra'
    ],
    'thane': [
      'kalyan', 'dombivli', 'ulhasnagar', 'bhiwandi', 'mira-bhayandar',
      'mira bhayandar', 'ambernath', 'badlapur', 'murbad', 'shahapur'
    ],
    'mumbai': [
      'bombay', 'bandra', 'andheri', 'dadar', 'kurla', 'borivali', 'fort',
      'churchgate', 'chembur', 'ghatkopar', 'mulund', 'navi mumbai', 'vashi'
    ],
    'nagpur': [
      'kamthi', 'ramtek', 'katol', 'narkhed', 'savner', 'umred', 'kuhi',
      'bhivapur', 'hingna', 'sitabuldi', 'dharampeth'
    ],
    'jalgaon': ['bhusawal', 'amalner', 'chalisgaon', 'chopda', 'pachora', 'jamner'],
    'dhule': ['shirpur', 'sakri', 'sindkheda'],
    'sangli': ['miraj', 'tasgaon', 'islampur', 'walwa', 'vita', 'jat', 'shirala'],
    'nanded': ['mukhed', 'degloor', 'kandhar', 'kinwat', 'biloli', 'hadgaon'],
    'latur': ['udgir', 'ahmadpur', 'ausa', 'nilanga', 'renapur', 'chakur'],
    'dharashiv': ['osmanabad', 'tuljapur', 'omerga', 'kalamb', 'bhoom', 'paranda'],
    'amravati': ['achlapur', 'chandur', 'morshi', 'warud', 'daryapur', 'anjangaon']
  },

  // Pre-configured bank branches across Maharashtra districts
  districtBanks: {
    'ahilyanagar': [
      { bank: 'State Bank of India', branch: 'Ahilyanagar Main Branch', ifsc: 'SBIN0000301' },
      { bank: 'State Bank of India', branch: 'Station Road Ahilyanagar', ifsc: 'SBIN0006725' },
      { bank: 'State Bank of India', branch: 'Savedi Ahilyanagar', ifsc: 'SBIN0011504' },
      { bank: 'State Bank of India', branch: 'Sangamner Branch', ifsc: 'SBIN0000472' },
      { bank: 'State Bank of India', branch: 'Shrirampur Branch', ifsc: 'SBIN0000484' },
      { bank: 'State Bank of India', branch: 'Rahuri Branch', ifsc: 'SBIN0000465' },
      { bank: 'State Bank of India', branch: 'Shirdi Branch', ifsc: 'SBIN0005152' },
      { bank: 'State Bank of India', branch: 'Kopargaon Branch', ifsc: 'SBIN0000413' },
      { bank: 'Bank of Maharashtra', branch: 'Ahilyanagar Main (Kapad Bazar)', ifsc: 'MAHB0000007' },
      { bank: 'Bank of Maharashtra', branch: 'Savedi Road Branch', ifsc: 'MAHB0000965' },
      { bank: 'Bank of Maharashtra', branch: 'MIDC Ahilyanagar', ifsc: 'MAHB0001046' },
      { bank: 'Bank of Maharashtra', branch: 'Sangamner Branch', ifsc: 'MAHB0000046' },
      { bank: 'Bank of Maharashtra', branch: 'Shrirampur Branch', ifsc: 'MAHB0000054' },
      { bank: 'Bank of Maharashtra', branch: 'Rahuri Branch', ifsc: 'MAHB0000215' },
      { bank: 'Ahmednagar District Central Co-op Bank', branch: 'Head Office Ahilyanagar', ifsc: 'ADCC0000001' },
      { bank: 'Ahmednagar District Central Co-op Bank', branch: 'Savedi Branch', ifsc: 'ADCC0000002' },
      { bank: 'Ahmednagar District Central Co-op Bank', branch: 'Sangamner Branch', ifsc: 'ADCC0000012' },
      { bank: 'Bank of Baroda', branch: 'Ahilyanagar Branch', ifsc: 'BARB0AHMEDN' },
      { bank: 'Central Bank of India', branch: 'Ahilyanagar Branch', ifsc: 'CBIN0280655' },
      { bank: 'Union Bank of India', branch: 'Ahilyanagar Main', ifsc: 'UBIN0531553' },
      { bank: 'Canara Bank', branch: 'Ahilyanagar', ifsc: 'CNRB0002570' },
      { bank: 'Punjab National Bank', branch: 'Ahilyanagar Branch', ifsc: 'PUNB0000800' },
      { bank: 'HDFC Bank', branch: 'Station Road Ahilyanagar', ifsc: 'HDFC0000767' },
      { bank: 'ICICI Bank', branch: 'Savedi Ahilyanagar', ifsc: 'ICIC0000654' },
      { bank: 'Axis Bank', branch: 'Ahilyanagar Branch', ifsc: 'UTIB0000645' }
    ],
    'pune': [
      { bank: 'State Bank of India', branch: 'Pune Main Branch (Collector Office)', ifsc: 'SBIN0000454' },
      { bank: 'State Bank of India', branch: 'Hadapsar / Haveli', ifsc: 'SBIN0001114' },
      { bank: 'State Bank of India', branch: 'Baramati Branch', ifsc: 'SBIN0000321' },
      { bank: 'State Bank of India', branch: 'Kothrud Pune', ifsc: 'SBIN0004114' },
      { bank: 'Bank of Maharashtra', branch: 'Bajirao Road (Lokmangal H.O.)', ifsc: 'MAHB0000001' },
      { bank: 'Bank of Maharashtra', branch: 'Khadki / Haveli Branch', ifsc: 'MAHB0000028' },
      { bank: 'Bank of Maharashtra', branch: 'Hadapsar Branch', ifsc: 'MAHB0000452' },
      { bank: 'Bank of Maharashtra', branch: 'Baramati Branch', ifsc: 'MAHB0000073' },
      { bank: 'Pune District Central Co-op Bank (PDCC)', branch: 'Head Office Pune', ifsc: 'PDCB0000001' },
      { bank: 'Pune District Central Co-op Bank (PDCC)', branch: 'Baramati Branch', ifsc: 'PDCB0000015' },
      { bank: 'Bank of Baroda', branch: 'Camp Pune', ifsc: 'BARB0CAMPUN' },
      { bank: 'Central Bank of India', branch: 'Pune Cantt', ifsc: 'CBIN0280644' },
      { bank: 'Union Bank of India', branch: 'Deccan Gymkhana Pune', ifsc: 'UBIN0531782' },
      { bank: 'Canara Bank', branch: 'Sadashiv Peth Pune', ifsc: 'CNRB0000216' },
      { bank: 'HDFC Bank', branch: 'FC Road Pune', ifsc: 'HDFC0000007' },
      { bank: 'ICICI Bank', branch: 'Bund Garden Pune', ifsc: 'ICIC0000005' },
      { bank: 'Axis Bank', branch: 'Pune Main Branch', ifsc: 'UTIB0000037' }
    ],
    'chhatrapati sambhajinagar': [
      { bank: 'State Bank of India', branch: 'Chhatrapati Sambhajinagar (Aurangabad) Main', ifsc: 'SBIN0000318' },
      { bank: 'State Bank of India', branch: 'CIDCO Sambhajinagar', ifsc: 'SBIN0003449' },
      { bank: 'Bank of Maharashtra', branch: 'Sambhajinagar Main Branch', ifsc: 'MAHB0000016' },
      { bank: 'Bank of Maharashtra', branch: 'Kranti Chowk Branch', ifsc: 'MAHB0000624' },
      { bank: 'Aurangabad District Central Co-op Bank', branch: 'Head Office', ifsc: 'ADCB0000001' },
      { bank: 'Bank of Baroda', branch: 'Sambhajinagar Main', ifsc: 'BARB0AURANG' },
      { bank: 'Central Bank of India', branch: 'Sambhajinagar', ifsc: 'CBIN0280662' },
      { bank: 'Union Bank of India', branch: 'Station Road Sambhajinagar', ifsc: 'UBIN0531561' }
    ],
    'nashik': [
      { bank: 'State Bank of India', branch: 'Nashik Main Branch', ifsc: 'SBIN0000437' },
      { bank: 'State Bank of India', branch: 'Canada Corner Nashik', ifsc: 'SBIN0007246' },
      { bank: 'Bank of Maharashtra', branch: 'Nashik City Branch', ifsc: 'MAHB0000018' },
      { bank: 'Bank of Maharashtra', branch: 'Panchavati Branch', ifsc: 'MAHB0000214' },
      { bank: 'Nashik District Central Co-op Bank (NDCC)', branch: 'Head Office', ifsc: 'NDCC0000001' },
      { bank: 'Bank of Baroda', branch: 'Nashik Main', ifsc: 'BARB0NASHIK' },
      { bank: 'Central Bank of India', branch: 'Nashik Branch', ifsc: 'CBIN0280650' },
      { bank: 'Union Bank of India', branch: 'Nashik Main', ifsc: 'UBIN0531774' }
    ],
    'satara': [
      { bank: 'State Bank of India', branch: 'Satara Main Branch', ifsc: 'SBIN0000473' },
      { bank: 'State Bank of India', branch: 'Karad Branch', ifsc: 'SBIN0000403' },
      { bank: 'Bank of Maharashtra', branch: 'Satara Branch', ifsc: 'MAHB0000010' },
      { bank: 'Bank of Maharashtra', branch: 'Karad Branch', ifsc: 'MAHB0000045' },
      { bank: 'Satara District Central Co-op Bank', branch: 'Head Office', ifsc: 'SDCC0000001' },
      { bank: 'Bank of Baroda', branch: 'Satara', ifsc: 'BARB0SATARA' },
      { bank: 'Union Bank of India', branch: 'Satara', ifsc: 'UBIN0531812' }
    ],
    'solapur': [
      { bank: 'State Bank of India', branch: 'Solapur Main Branch', ifsc: 'SBIN0000483' },
      { bank: 'State Bank of India', branch: 'Pandharpur Branch', ifsc: 'SBIN0000446' },
      { bank: 'Bank of Maharashtra', branch: 'Solapur Branch', ifsc: 'MAHB0000011' },
      { bank: 'Bank of Maharashtra', branch: 'Pandharpur Branch', ifsc: 'MAHB0000072' },
      { bank: 'Solapur District Central Co-op Bank', branch: 'Head Office', ifsc: 'SDCB0000001' },
      { bank: 'Bank of Baroda', branch: 'Solapur', ifsc: 'BARB0SOLAPU' },
      { bank: 'Central Bank of India', branch: 'Solapur', ifsc: 'CBIN0280657' }
    ],
    'kolhapur': [
      { bank: 'State Bank of India', branch: 'Kolhapur Main', ifsc: 'SBIN0000412' },
      { bank: 'State Bank of India', branch: 'Ichalkaranji', ifsc: 'SBIN0000388' },
      { bank: 'Bank of Maharashtra', branch: 'Laxmipuri Kolhapur', ifsc: 'MAHB0000008' },
      { bank: 'Bank of Maharashtra', branch: 'Ichalkaranji', ifsc: 'MAHB0000044' },
      { bank: 'Kolhapur District Central Co-op Bank', branch: 'Head Office', ifsc: 'KDCC0000001' },
      { bank: 'Bank of Baroda', branch: 'Kolhapur Main', ifsc: 'BARB0KOLHAP' }
    ],
    'thane': [
      { bank: 'State Bank of India', branch: 'Thane Main Branch', ifsc: 'SBIN0000488' },
      { bank: 'State Bank of India', branch: 'Kalyan Branch', ifsc: 'SBIN0000399' },
      { bank: 'Bank of Maharashtra', branch: 'Thane Station', ifsc: 'MAHB0000085' },
      { bank: 'Bank of Maharashtra', branch: 'Dombivli Branch', ifsc: 'MAHB0000125' },
      { bank: 'Thane District Central Co-op Bank', branch: 'Head Office', ifsc: 'TDCB0000001' }
    ],
    'mumbai': [
      { bank: 'State Bank of India', branch: 'Mumbai Main Branch (Fort)', ifsc: 'SBIN0000300' },
      { bank: 'State Bank of India', branch: 'Dadar Branch', ifsc: 'SBIN0000353' },
      { bank: 'Bank of Maharashtra', branch: 'Fort Mumbai', ifsc: 'MAHB0000002' },
      { bank: 'Bank of Maharashtra', branch: 'Dadar West', ifsc: 'MAHB0000034' },
      { bank: 'Bank of Baroda', branch: 'Fort Mumbai', ifsc: 'BARB0FORTXX' },
      { bank: 'Union Bank of India', branch: 'Nariman Point Mumbai', ifsc: 'UBIN0531000' }
    ],
    'nagpur': [
      { bank: 'State Bank of India', branch: 'Nagpur Main Branch', ifsc: 'SBIN0000432' },
      { bank: 'Bank of Maharashtra', branch: 'Sitabuldi Nagpur', ifsc: 'MAHB0000005' },
      { bank: 'Bank of Baroda', branch: 'Dharampeth Nagpur', ifsc: 'BARB0DHARAM' },
      { bank: 'Nagpur District Central Co-op Bank', branch: 'Head Office', ifsc: 'NDCB0000001' }
    ],
    'jalgaon': [
      { bank: 'State Bank of India', branch: 'Jalgaon Main Branch', ifsc: 'SBIN0000392' },
      { bank: 'Bank of Maharashtra', branch: 'Jalgaon City Branch', ifsc: 'MAHB0000043' },
      { bank: 'Jalgaon District Central Co-op Bank', branch: 'Head Office', ifsc: 'JDCC0000001' }
    ],
    'sangli': [
      { bank: 'State Bank of India', branch: 'Sangli Main Branch', ifsc: 'SBIN0000475' },
      { bank: 'Bank of Maharashtra', branch: 'Sangli Branch', ifsc: 'MAHB0000022' },
      { bank: 'Sangli District Central Co-op Bank', branch: 'Head Office', ifsc: 'SDCC0000002' }
    ]
  },

  // General popular banks for SHG accounts across Maharashtra & India
  defaultBanks: [
    { bank: 'State Bank of India', branch: 'Main Branch', ifsc: 'SBIN0000301' },
    { bank: 'Bank of Maharashtra', branch: 'Main Branch', ifsc: 'MAHB0000007' },
    { bank: 'District Central Co-operative Bank', branch: 'District Central Branch', ifsc: 'ADCC0000001' },
    { bank: 'Bank of Baroda', branch: 'City Branch', ifsc: 'BARB0AHMEDN' },
    { bank: 'Central Bank of India', branch: 'Main Branch', ifsc: 'CBIN0280655' },
    { bank: 'Union Bank of India', branch: 'Main Branch', ifsc: 'UBIN0531553' },
    { bank: 'Punjab National Bank', branch: 'Town Branch', ifsc: 'PUNB0000800' },
    { bank: 'Canara Bank', branch: 'Main Branch', ifsc: 'CNRB0002570' },
    { bank: 'HDFC Bank', branch: 'Retail Branch', ifsc: 'HDFC0000767' },
    { bank: 'ICICI Bank', branch: 'Branch', ifsc: 'ICIC0000654' },
    { bank: 'Axis Bank', branch: 'Branch', ifsc: 'UTIB0000645' }
  ],

  /**
   * Retrieves bank branch list for a given location query (supports district, taluka, town, partial prefix)
   */
  getBanksForLocation(query) {
    if (!query) return this.defaultBanks;

    const raw = String(query).toLowerCase();
    const clean = raw
      .replace(/district/gi, '')
      .replace(/dist/gi, '')
      .replace(/taluka/gi, '')
      .replace(/village/gi, '')
      .replace(/town/gi, '')
      .replace(/city/gi, '')
      .replace(/[0-9,\-_/\\#.]/g, ' ')
      .trim();

    if (!clean) return this.defaultBanks;

    const words = clean.split(/\s+/).filter(w => w.length >= 3);

    // 1. Direct key match or token match on districtBanks
    for (const [key, list] of Object.entries(this.districtBanks)) {
      if (clean.includes(key) || key.includes(clean)) {
        return list;
      }
      for (const w of words) {
        if (key.startsWith(w) || w.startsWith(key) || key.includes(w) || w.includes(key)) {
          return list;
        }
      }
    }

    // 2. Alias match (talukas, former names, short names like 'ahilya', 'nagar', 'baramati')
    for (const [key, aliases] of Object.entries(this.locationAliases)) {
      for (const alias of aliases) {
        if (clean.includes(alias) || alias.includes(clean)) {
          return this.districtBanks[key] || this.defaultBanks;
        }
        for (const w of words) {
          if (alias.startsWith(w) || w.startsWith(alias) || alias.includes(w) || w.includes(alias)) {
            return this.districtBanks[key] || this.defaultBanks;
          }
        }
      }
    }

    return this.defaultBanks;
  },

  /**
   * Populates a select element with banks matching the given district / location.
   * Includes option for manual entry if bank is not found.
   */
  populateSelect(selectElement, locationQuery, currentIfsc = '') {
    if (!selectElement) return;

    const banks = this.getBanksForLocation(locationQuery);

    let displayLocation = 'Your Location';
    if (locationQuery && typeof locationQuery === 'string') {
      const parts = locationQuery.trim().split(/\s+/).filter(p => p.length >= 2 && !/^\d+$/.test(p));
      if (parts.length > 0) {
        displayLocation = parts[0].charAt(0).toUpperCase() + parts[0].slice(1);
      }
    }

    selectElement.innerHTML = `
      <option value="">-- Choose Bank Branch (Near ${displayLocation}) --</option>
    `;

    banks.forEach(b => {
      const isSelected = currentIfsc && currentIfsc.toUpperCase() === b.ifsc.toUpperCase();
      const opt = document.createElement('option');
      opt.value = b.ifsc;
      opt.setAttribute('data-bank', b.bank);
      opt.setAttribute('data-bank-name', b.bank);
      opt.setAttribute('data-branch', b.branch);
      opt.setAttribute('data-ifsc', b.ifsc);
      opt.textContent = `${b.bank} - ${b.branch} (${b.ifsc})`;
      if (isSelected) opt.selected = true;
      selectElement.appendChild(opt);
    });

    // Explicit Other Bank / Manual option (Required by User!)
    const manualOpt = document.createElement('option');
    manualOpt.value = 'OTHER';
    manualOpt.setAttribute('data-bank', '');
    manualOpt.setAttribute('data-bank-name', '');
    manualOpt.setAttribute('data-branch', '');
    manualOpt.setAttribute('data-ifsc', '');
    manualOpt.textContent = '✎ Other Bank (Enter Name & IFSC Manually)';
    selectElement.appendChild(manualOpt);
  },

  // Alias for backward and forward compatibility
  populateDropdown(selectElement, locationQuery, currentIfsc = '') {
    return this.populateSelect(selectElement, locationQuery, currentIfsc);
  },

  // Offline PIN prefix mapping for instant reliable fallback
  pinPrefixMap: {
    '414': 'ahilyanagar',
    '411': 'pune',
    '412': 'pune',
    '431': 'chhatrapati sambhajinagar',
    '422': 'nashik',
    '415': 'satara',
    '413': 'solapur',
    '416': 'kolhapur',
    '400': 'mumbai',
    '401': 'thane',
    '421': 'thane',
    '440': 'nagpur',
    '425': 'jalgaon',
    '424': 'dhule',
    '416': 'sangli',
    '444': 'amravati'
  },

  /**
   * Searches nearby banks by 6-digit Indian PIN Code (Requirement 8).
   * Free Public API: https://api.postalpincode.in/pincode/{pin} (zero cost) with offline prefix fallback.
   */
  async lookupByPinCode(pincode, selectElement, statusElement, options = {}) {
    const pin = String(pincode || '').trim();
    if (!pin) {
      if (statusElement) statusElement.textContent = '';
      return;
    }

    if (!/^[1-9][0-9]{5}$/.test(pin)) {
      if (pin.length === 6) {
        if (statusElement) {
          statusElement.textContent = 'Invalid PIN Code. Must be 6 numeric digits.';
          statusElement.style.color = '#dc2626';
        }
      }
      return;
    }

    if (statusElement) {
      statusElement.textContent = `Finding nearby banks for PIN [${pin}]...`;
      statusElement.style.color = '#0284c7';
    }

    let detectedDistrict = '';
    let detectedTaluka = '';

    // Step 1: Query free Indian postal API
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 4000);
      const res = await fetch(`https://api.postalpincode.in/pincode/${pin}`, { signal: controller.signal });
      clearTimeout(timeoutId);

      if (res.ok) {
        const data = await res.json();
        if (Array.isArray(data) && data[0] && data[0].Status === 'Success' && Array.isArray(data[0].PostOffice) && data[0].PostOffice.length > 0) {
          const po = data[0].PostOffice[0];
          detectedDistrict = po.District || '';
          detectedTaluka = po.Block || po.Taluk || po.Division || '';

          // Optionally update location input fields if provided
          if (options.districtInput && (!options.districtInput.value || options.districtInput.value.trim() === '')) {
            options.districtInput.value = detectedDistrict;
          }
          if (options.talukaInput && (!options.talukaInput.value || options.talukaInput.value.trim() === '')) {
            options.talukaInput.value = detectedTaluka;
          }
        }
      }
    } catch (e) {
      console.warn('Live postal PIN lookup fetch skipped/timeout:', e.message);
    }

    // Step 2: Fallback to offline prefix directory if postal API was empty/offline
    if (!detectedDistrict) {
      const prefix3 = pin.substring(0, 3);
      if (this.pinPrefixMap[prefix3]) {
        detectedDistrict = this.pinPrefixMap[prefix3];
      }
    }

    // Step 3: Populate nearby banks dropdown
    const locationQuery = [detectedDistrict, detectedTaluka, pin].filter(Boolean).join(' ');
    const banks = this.getBanksForLocation(locationQuery);

    if (selectElement) {
      this.populateSelect(selectElement, locationQuery);
    }

    if (statusElement) {
      if (banks && banks.length > 0) {
        const placeName = detectedDistrict ? (detectedDistrict.charAt(0).toUpperCase() + detectedDistrict.slice(1)) : 'your area';
        statusElement.textContent = `✓ Found ${banks.length} nearby banks in ${placeName} for PIN [${pin}]`;
        statusElement.style.color = '#059669';
      } else {
        statusElement.textContent = `No banks found for PIN [${pin}]. Please select "Other Bank" to enter manually.`;
        statusElement.style.color = '#d97706';
      }
    }

    return { district: detectedDistrict, taluka: detectedTaluka, banks };
  },

  /**
   * Verifies an IFSC code strictly (Requirement 9).
   * At minimum validates:
   * - Required format (^[A-Z]{4}0[A-Z0-9]{6}$)
   * - Correct length (11 characters)
   * - 5th character must be '0'
   * - Local verified database check
   * - Free public Razorpay IFSC lookup (zero cost)
   * Returns { valid: true, bank, branch, ifsc, city, district } or { valid: false, message }
   */
  async lookupIfsc(ifsc) {
    const cleanIfsc = (ifsc || '').trim().toUpperCase();
    if (!cleanIfsc || cleanIfsc.length !== 11) {
      return { valid: false, message: 'Invalid IFSC Code. IFSC must be exactly 11 characters (e.g. SBIN0000301).' };
    }

    // Format validation: 4 letters, '0', 6 alphanumeric
    const ifscRegex = /^[A-Z]{4}0[A-Z0-9]{6}$/;
    if (!ifscRegex.test(cleanIfsc)) {
      return { valid: false, message: 'Invalid IFSC Code. Format must be 4 letters, followed by 0, and 6 letters/digits (e.g. SBIN0000301).' };
    }

    // 1. Check local district banks dictionary
    for (const bankList of Object.values(this.districtBanks)) {
      const match = bankList.find(b => b.ifsc === cleanIfsc);
      if (match) {
        return {
          valid: true,
          bank: match.bank,
          branch: match.branch,
          ifsc: match.ifsc,
          source: 'local'
        };
      }
    }

    // 2. Check default list
    const defaultMatch = this.defaultBanks.find(b => b.ifsc === cleanIfsc);
    if (defaultMatch) {
      return {
        valid: true,
        bank: defaultMatch.bank,
        branch: defaultMatch.branch,
        ifsc: defaultMatch.ifsc,
        source: 'local'
      };
    }

    // 3. Fallback: Query live Razorpay open IFSC lookup (100% free and open, zero cost)
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 4500);
      const res = await fetch(`https://ifsc.razorpay.com/${cleanIfsc}`, { signal: controller.signal });
      clearTimeout(timeoutId);

      if (res.ok) {
        const data = await res.json();
        return {
          valid: true,
          bank: data.BANK || 'Bank',
          branch: data.BRANCH || '',
          ifsc: data.IFSC || cleanIfsc,
          city: data.CITY || '',
          district: data.DISTRICT || '',
          state: data.STATE || '',
          source: 'live'
        };
      } else if (res.status === 404) {
        return {
          valid: false,
          message: 'Invalid IFSC Code. Please enter a valid IFSC Code.'
        };
      }
    } catch (e) {
      console.warn('Live IFSC lookup fetch failed:', e.message);
      return {
        valid: false,
        unverified: true,
        message: 'Could not connect to IFSC verification service. Please check network and confirm code.'
      };
    }

    return {
      valid: false,
      message: 'Invalid IFSC Code. Please enter a valid IFSC Code.'
    };
  },

  // Alias for verifyIFSC
  async verifyIFSC(ifsc) {
    return this.lookupIfsc(ifsc);
  }
};

window.BankDirectory = BankDirectory;

