// ==========================================
// 1. STATE
// ==========================================
const state = {
    sensors: {},
    actuators: {},
    logs: [],
    sensorData: {},
    sensorHistory: {},
    sensorCharts: {},
    groupCharts: {},           // chart instances for grouped chart view
    renderedChartKeys: [],     // track rendered chart keys to avoid DOM thrashing
    knownSensors: new Set(),
    knownActuators: new Set(),
    viewMode: 'cards',        // 'cards' or 'charts'
    groupBy: 'origin',        // US06: 'origin' or 'type'
    safeRanges: JSON.parse(localStorage.getItem('safeRanges') || '{}'), // US14
    unitPreferences: JSON.parse(localStorage.getItem('unitPreferences') || '{}'), // Unit conversion preferences
    logFilter: 'all',         // US13: 'all','rule','command','alert','config'
    networkStats: {            // US11
        messagesReceived: 0,
        lastMessageTime: null,
        messageTimestamps: []
    }
};

// ==========================================
// 1c. UNIT CONVERSION SYSTEM
// ==========================================
const UNIT_FAMILIES = [
    {
        units: ['°C', '°F', 'K'],
        toBase:   { '°C': v => v,             '°F': v => (v - 32) * 5 / 9, 'K': v => v - 273.15 },
        fromBase: { '°C': v => v,             '°F': v => v * 9 / 5 + 32,   'K': v => v + 273.15 }
    },
    {
        units: ['kW', 'W'],
        toBase:   { 'kW': v => v,    'W': v => v / 1000 },
        fromBase: { 'kW': v => v,    'W': v => v * 1000 }
    },
    {
        units: ['kWh', 'Wh'],
        toBase:   { 'kWh': v => v,   'Wh': v => v / 1000 },
        fromBase: { 'kWh': v => v,   'Wh': v => v * 1000 }
    },
    {
        units: ['V', 'mV', 'kV'],
        toBase:   { 'V': v => v,     'mV': v => v / 1000,  'kV': v => v * 1000 },
        fromBase: { 'V': v => v,     'mV': v => v * 1000,  'kV': v => v / 1000 }
    },
    {
        units: ['A', 'mA'],
        toBase:   { 'A': v => v,     'mA': v => v / 1000 },
        fromBase: { 'A': v => v,     'mA': v => v * 1000 }
    },
    {
        units: ['ug/m3', 'mg/m3'],
        toBase:   { 'ug/m3': v => v, 'mg/m3': v => v * 1000 },
        fromBase: { 'ug/m3': v => v, 'mg/m3': v => v / 1000 }
    },
    {
        units: ['L/min', 'mL/min'],
        toBase:   { 'L/min': v => v,  'mL/min': v => v / 1000 },
        fromBase: { 'L/min': v => v,  'mL/min': v => v * 1000 }
    },
    {
        units: ['L', 'mL', 'gal'],
        toBase:   { 'L': v => v,     'mL': v => v / 1000,       'gal': v => v * 3.78541 },
        fromBase: { 'L': v => v,     'mL': v => v * 1000,       'gal': v => v * 0.264172 }
    },
    {
        units: ['hPa', 'mbar', 'atm', 'mmHg'],
        toBase:   { 'hPa': v => v,   'mbar': v => v,            'atm': v => v * 1013.25,  'mmHg': v => v * 1.33322 },
        fromBase: { 'hPa': v => v,   'mbar': v => v,            'atm': v => v / 1013.25,  'mmHg': v => v / 1.33322 }
    }
];

// Build a lookup: unit string → family object
const UNIT_FAMILY_MAP = {};
UNIT_FAMILIES.forEach(family => {
    family.units.forEach(u => { UNIT_FAMILY_MAP[u] = family; });
});

function getCompatibleUnits(unit) {
    const family = UNIT_FAMILY_MAP[unit];
    return family ? family.units : null;
}

function convertUnit(value, fromUnit, toUnit) {
    if (fromUnit === toUnit || typeof value !== 'number') return value;
    const family = UNIT_FAMILY_MAP[fromUnit];
    if (!family || !family.toBase[fromUnit] || !family.fromBase[toUnit]) return value;
    const base = family.toBase[fromUnit](value);
    return family.fromBase[toUnit](base);
}

function formatConverted(value) {
    if (typeof value !== 'number') return value;
    // Use smart rounding: more decimals for small values
    if (Math.abs(value) < 0.01) return value.toFixed(4);
    if (Math.abs(value) < 1) return value.toFixed(3);
    if (Math.abs(value) < 100) return value.toFixed(2);
    return value.toFixed(1);
}

function cycleUnit(sensorKey, originalUnit) {
    const compatible = getCompatibleUnits(originalUnit);
    if (!compatible || compatible.length <= 1) return;
    const current = state.unitPreferences[sensorKey] || originalUnit;
    const idx = compatible.indexOf(current);
    const next = compatible[(idx + 1) % compatible.length];
    state.unitPreferences[sensorKey] = next;
    localStorage.setItem('unitPreferences', JSON.stringify(state.unitPreferences));
    renderDashboard();
}

// ==========================================
// 1b. SENSOR TYPE CLASSIFICATION (US06)
// ==========================================
const SENSOR_TYPES = {
    'Power':       { icon: 'bi-lightning-charge', color: '#fbbf24', keywords: ['power', 'voltage', 'current', 'cumulative', 'solar', 'power_bus', 'power_consumption'] },
    'Air Quality': { icon: 'bi-wind',             color: '#60a5fa', keywords: ['voc', 'co2', 'pm1', 'pm25', 'pm10', 'aqi', 'air_quality'] },
    'Climate':     { icon: 'bi-thermometer-half',  color: '#fb7185', keywords: ['temperature', 'humidity', 'pressure', 'greenhouse', 'corridor'] },
    'Water':       { icon: 'bi-droplet',           color: '#2dd4bf', keywords: ['level_pct', 'level_liters', 'flow', 'ph', 'water', 'hydroponic'] },
    'Safety':      { icon: 'bi-shield-check',      color: '#c084fc', keywords: ['radiation', 'cycles', 'airlock', 'life_support'] },
};

function classifySensorType(sensorId) {
    const idLower = sensorId.toLowerCase();
    const data = state.sensorData[sensorId];
    const metricNames = data ? Object.keys(data.metrics).map(m => m.toLowerCase()) : [];
    for (const [type, cfg] of Object.entries(SENSOR_TYPES)) {
        if (cfg.keywords.some(k => idLower.includes(k) || metricNames.some(mn => mn.includes(k)))) return type;
    }
    return 'Other';
}

function relativeTime(isoString) {
    const d = new Date(isoString);
    const hh = String(d.getHours()).padStart(2, '0');
    const mm = String(d.getMinutes()).padStart(2, '0');
    const ss = String(d.getSeconds()).padStart(2, '0');
    return hh + ':' + mm + ':' + ss;
}

// ==========================================
// 2. TOASTS
// ==========================================
function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;
    const toast = document.createElement('div');
    toast.className = `toast-item toast-${type}`;
    const icons = { info: 'bi-info-circle', success: 'bi-check-circle', warning: 'bi-exclamation-triangle', error: 'bi-x-circle' };
    toast.innerHTML = `<i class="bi ${icons[type] || icons.info}"></i><span>${message}</span>`;
    container.appendChild(toast);
    setTimeout(() => { if (toast.parentNode) toast.remove(); }, 4200);
}

// ==========================================
// 3. WEBSOCKET CONNECTION
// ==========================================
const stompClient = new window.StompJs.Client({
    webSocketFactory: () => new SockJS('/web-engine'),
    reconnectDelay: 5000,
    onConnect: () => {
        document.getElementById('connection-status').innerHTML =
            '<span class="status-pill online"><span class="status-dot"></span> Online</span>';
        showToast('Connected to telemetry stream', 'success');

        stompClient.subscribe('/topic/sensors', (message) => {
            const data = JSON.parse(message.body);
            console.log('📊 Sensor message received:', data);
            handleSensorDiscovery(data);
        });

        stompClient.subscribe('/topic/actuators/status', (message) => {
            const data = JSON.parse(message.body);
            console.log('🔌 Actuator status received:', data);
            handleActuatorStatus(data);
        });

        stompClient.subscribe('/topic/rules', (message) => {
            const data = JSON.parse(message.body);
            console.log('🧠 Automation rule snapshot received:', data);
            handleRuleSnapshot(data);
        });

        // Request full actuator snapshot on every client reconnect/refresh.
        stompClient.publish({ destination: '/app/actuators/sync', body: JSON.stringify({ reason: 'page_refresh' }) });

        // Request full sensor snapshot on every client reconnect/refresh.
        stompClient.publish({ destination: '/app/sensors/sync', body: JSON.stringify({ reason: 'page_refresh' }) });

        // Request automation rules snapshot on every client reconnect/refresh.
        rules.length = 0;
        renderRules();
        updateStatsBadges();
        stompClient.publish({ destination: '/app/rules/sync', body: JSON.stringify({ reason: 'page_refresh' }) });
    },
    onDisconnect: () => {
        document.getElementById('connection-status').innerHTML =
            '<span class="status-pill offline"><span class="status-dot"></span> Offline</span>';
        showToast('Disconnected from telemetry', 'error');
    },
    onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message']);
    }
});

// ==========================================
// 4. SENSOR & ACTUATOR DISCOVERY
// ==========================================
const MAX_CHART_POINTS = 40;
const CHART_COLORS = [
    '#818cf8', '#34d399', '#fbbf24', '#fb7185', '#c084fc',
    '#60a5fa', '#fb923c', '#e879f9', '#2dd4bf', '#a3e635'
];
let colorIdx = 0;
function nextColor() {
    return CHART_COLORS[colorIdx++ % CHART_COLORS.length];
}

function handleSensorDiscovery(sensorData) {
    // SensorData structure: { header, sensor_id, status, metrics[] }
    const sensorId = sensorData.sensor_id;
    const timestamp = sensorData.header.timestamp;
    const isNew = !state.knownSensors.has(sensorId);

    if (isNew) {
        state.knownSensors.add(sensorId);
        addLog('config', `New sensor discovered: ${sensorId}`);
        showToast(`Sensor discovered: ${sensorId}`, 'info');
    }

    if (!state.sensorData[sensorId]) {
        state.sensorData[sensorId] = { metrics: {}, status: 'ok', lastUpdate: timestamp };
    }
    state.sensorData[sensorId].lastUpdate = timestamp;
    state.sensorData[sensorId].status = sensorData.status || 'ok';

    // US11: track message rate
    state.networkStats.messagesReceived++;
    state.networkStats.lastMessageTime = Date.now();
    state.networkStats.messageTimestamps.push(Date.now());
    const cutoff = Date.now() - 60000;
    state.networkStats.messageTimestamps = state.networkStats.messageTimestamps.filter(t => t > cutoff);

    if (sensorData.metrics && sensorData.metrics.length > 0) {
        sensorData.metrics.forEach(metric => {
            state.sensorData[sensorId].metrics[metric.name] = {
                value: metric.value,
                unit: metric.unit
            };

            // Key sensors by sensorId/metricName for unique identification
            const sensorKey = sensorId + '/' + metric.name;
            state.sensors[sensorKey] = {
                value: metric.value,
                unit: metric.unit,
                status: sensorData.status,
                lastUpdate: timestamp,
                origin: sensorId
            };

            const chartKey = sensorId + '::' + metric.name;
            if (!state.sensorHistory[chartKey]) {
                state.sensorHistory[chartKey] = [];
            }
            state.sensorHistory[chartKey].push({ time: timestamp, value: metric.value });
            if (state.sensorHistory[chartKey].length > MAX_CHART_POINTS) {
                state.sensorHistory[chartKey].shift();
            }

            updateSensorChart(chartKey, metric.name, metric.unit, sensorId);

            // US14: check safe ranges
            checkSafeRange(sensorKey, metric.value);
        });
    }

    updateStatsBadges();
    scheduleRender();
}

function handleActuatorStatus(actuatorStatus) {
    // Supports both ActuatorStatus and legacy envelope-like payloads.
    const actuatorId = actuatorStatus?.actuator_id
        || actuatorStatus?.actuatorId
        || actuatorStatus?.payload?.subject_id;
    if (!actuatorId) return;

    const timestamp = actuatorStatus?.header?.timestamp || actuatorStatus?.updated_at || new Date().toISOString();
    const isNew = !state.knownActuators.has(actuatorId);

    if (isNew) {
        state.knownActuators.add(actuatorId);
        addLog('config', `New actuator discovered: ${actuatorId}`);
        showToast(`Actuator discovered: ${actuatorId}`, 'info');
    }

    // Get the actual state from the message
    const metricState = Array.isArray(actuatorStatus?.payload?.metrics)
        ? actuatorStatus.payload.metrics.find(m => m?.name === 'actual_state')?.value
        : undefined;
    const actuatorState = actuatorStatus?.actual_state || actuatorStatus?.actualState || metricState || 'UNKNOWN';
    const previousState = state.actuators[actuatorId]?.state;

    // Update state
    state.actuators[actuatorId] = {
        state: actuatorState,
        lastUpdate: timestamp,
        updated_at: actuatorStatus.updated_at,
        pending: false
    };

    // Log state changes
    if (actuatorState !== previousState && previousState !== undefined) {
        addLog('command', `${actuatorId} state changed: ${previousState} → ${actuatorState}`);
        showToast(`${actuatorId}: now ${actuatorState}`, 'info');
    }

    updateStatsBadges();
    renderActuators();
}

function updateSensorChart(chartKey, metricName, unit, sensorId) {
    // When in charts mode, grouped charts are managed by renderChartsView
    if (state.viewMode === 'charts') {
        updateGroupedChart(chartKey);
        return;
    }
}

function updateGroupedChart(chartKey) {
    const chart = state.groupCharts[chartKey];
    if (!chart) return;
    const history = state.sensorHistory[chartKey];
    if (!history) return;
    chart.data.labels = history.map(p => new Date(p.time).toLocaleTimeString());
    chart.data.datasets[0].data = history.map(p => p.value);
    chart.update('none');
}

// ==========================================
// 5. RENDERING
// ==========================================
let renderScheduled = false;
function scheduleRender() {
    if (renderScheduled) return;
    renderScheduled = true;
    requestAnimationFrame(() => {
        renderDashboard();
        // Update Network page if it's currently visible
        if (!document.getElementById('network')?.classList.contains('d-none')) {
            renderNetworkHealth();
        }
        renderScheduled = false;
    });
}

function buildSensorGroups() {
    const groups = {};
    if (state.groupBy === 'type') {
        for (const [sensorId, sData] of Object.entries(state.sensorData)) {
            const type = classifySensorType(sensorId);
            if (!groups[type]) groups[type] = { sensors: [], icon: 'bi-cpu', color: 'var(--accent)' };
            const typeInfo = SENSOR_TYPES[type] || {};
            groups[type].icon = typeInfo.icon || 'bi-cpu';
            groups[type].color = typeInfo.color || 'var(--accent)';
            for (const [metricName, metric] of Object.entries(sData.metrics)) {
                groups[type].sensors.push({
                    name: metricName,
                    value: metric.value,
                    unit: metric.unit,
                    status: sData.status,
                    lastUpdate: sData.lastUpdate,
                    origin: sensorId
                });
            }
        }
    } else {
        for (const [key, sensor] of Object.entries(state.sensors)) {
            const origin = sensor.origin || 'Telemetry';
            if (!groups[origin]) groups[origin] = { sensors: [], icon: 'bi-cpu', color: 'var(--accent)' };
            groups[origin].sensors.push({ name: key.split('/').pop(), displayKey: key, ...sensor });
        }
    }
    return groups;
}

function setViewMode(mode) {
    state.viewMode = mode;
    // Destroy grouped charts when switching away from charts
    if (mode === 'cards') {
        Object.values(state.groupCharts).forEach(c => { if (c && c.destroy) c.destroy(); });
        state.groupCharts = {};
        state.renderedChartKeys = [];
    }
    document.getElementById('view-cards-btn')?.classList.toggle('active', mode === 'cards');
    document.getElementById('view-charts-btn')?.classList.toggle('active', mode === 'charts');
    renderDashboard();
}

function renderDashboard() {
    const container = document.getElementById('sensor-groups-container');
    const groups = buildSensorGroups();
    const groupEntries = Object.entries(groups);

    if (groupEntries.length === 0) {
        container.innerHTML = `
            <div class="glass p-4">
                <div class="empty-state">
                    <i class="bi bi-broadcast"></i>
                    <p>Waiting for sensor discovery…</p>
                    <p class="empty-hint">Sensors will appear here automatically when detected on the network</p>
                </div>
            </div>`;
        return;
    }

    if (state.viewMode === 'charts') {
        renderChartsView(container, groups, groupEntries);
        return;
    }

    let html = '<div class="sensor-groups-flex">';
    for (const [groupName, group] of groupEntries) {
        const sensorCount = group.sensors.length;
        const sizeClass = sensorCount <= 2 ? 'group-sm' : sensorCount <= 4 ? 'group-md' : 'group-full';
        const escapedName = groupName.replace(/'/g, "\\'");
        html += `<div class="glass p-3 sensor-group-item ${sizeClass}">
            <div class="d-flex align-items-center justify-content-between mb-2">
                <div class="group-label mb-0"><i class="bi ${group.icon}" style="color:${group.color};"></i> ${groupName}</div>
            </div>
            <div class="row g-2">`;

        const colClass = sensorCount <= 2 ? 'col-12' : 'col-6 col-md-4 col-lg-3';

        group.sensors.forEach(sensor => {
            let cls = 'glass-inner sensor-card';
            if (sensor.status === 'warning') cls += ' warning';
            if (sensor.status === 'error') cls += ' danger';

            // US14: check if value is outside safe range
            const rangeKey = (sensor.origin || groupName) + '/' + sensor.name;
            const range = getSafeRangeConfig(rangeKey);
            let alarmHtml = '';
            const isOutOfRange = isSafeRangeBreached(range, sensor.value);
            if (range && isOutOfRange) {
                cls += ' alarm';
                alarmHtml = `<div class="alarm-badge"><i class="bi bi-exclamation-triangle-fill"></i> UNSAFE</div>`;
            }

            const originLabel = (state.groupBy === 'type' && sensor.origin)
                ? `<div class="sensor-origin" style="font-size:.6rem;color:var(--text-tertiary);font-family:'JetBrains Mono',monospace;margin-bottom:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;" title="${sensor.origin}">${sensor.origin}</div>`
                : '';

            // Unit conversion: determine displayed value and unit
            const sensorKey = rangeKey;
            const originalUnit = sensor.unit || '';
            const preferredUnit = state.unitPreferences[sensorKey] || originalUnit;
            const compatible = getCompatibleUnits(originalUnit);
            const hasConversion = compatible && compatible.length > 1;
            let displayValue = sensor.value;
            let displayUnit = originalUnit;
            if (hasConversion && preferredUnit !== originalUnit) {
                displayValue = convertUnit(sensor.value, originalUnit, preferredUnit);
                displayUnit = preferredUnit;
            }
            const formattedValue = (hasConversion && preferredUnit !== originalUnit) ? formatConverted(displayValue) : sensor.value;

            // Unit toggle button (only if there are compatible units)
            const escapedKey = sensorKey.replace(/'/g, "\\'");
            const escapedOrigUnit = originalUnit.replace(/'/g, "\\'");
            const unitBtnHtml = hasConversion
                ? `<button class="unit-toggle-btn" onclick="event.stopPropagation(); cycleUnit('${escapedKey}', '${escapedOrigUnit}')" title="Click to change unit">${displayUnit} <i class="bi bi-arrow-repeat"></i></button>`
                : `<span class="sensor-unit">${displayUnit}</span>`;

            // Build range label if a safe range is defined
            let rangeHtml = '';
            if (range) {
                const summary = formatSafeRangeSummary(range, originalUnit, preferredUnit, originalUnit, hasConversion);
                rangeHtml = `<div style="font-size:.63rem;color:var(--text-tertiary);font-family:'JetBrains Mono',monospace;margin-top:2px;"><i class="bi bi-shield-check" style="font-size:.55rem;"></i> ${summary}</div>`;
            }

            html += `
            <div class="${colClass}">
                <div class="${cls}" style="padding:.9rem 1.1rem;">
                    ${alarmHtml}
                    <div class="d-flex justify-content-between align-items-center mb-1">
                        <span class="sensor-name">${sensor.name.toUpperCase()}</span>
                        <span class="status-dot-sm ${sensor.status || 'ok'}" title="${sensor.status || 'ok'}"></span>
                    </div>
                    ${originLabel}
                    <div class="sensor-value"><span style="white-space:nowrap">${formattedValue}</span>${unitBtnHtml}</div>
                    ${rangeHtml}
                    <div class="d-flex justify-content-between align-items-center mt-1">
                        <div class="sensor-time">
                            <i class="bi bi-clock" style="font-size:.55rem;"></i>
                            ${relativeTime(sensor.lastUpdate)}
                        </div>
                        <button class="btn btn-glass btn-sm p-0" style="font-size:.65rem;line-height:1;padding:2px 5px!important;" onclick="event.stopPropagation(); forceRefreshSensor('${(sensor.origin || groupName).replace(/'/g, "\\'")}')"
                            title="Force update reading">
                            <i class="bi bi-arrow-clockwise"></i>
                        </button>
                    </div>
                </div>
            </div>`;
        });

        html += '</div></div>';
    }
    html += '</div>';

    container.innerHTML = html;
}

// ==========================================
// 5b. CHARTS VIEW (grouped)
// ==========================================
function renderChartsView(container, groups, groupEntries) {
    // Collect current chart keys to detect structure changes
    const currentKeys = [];
    for (const [groupName, group] of groupEntries) {
        group.sensors.forEach(s => {
            const chartKey = (s.origin || groupName) + '::' + s.name;
            currentKeys.push(chartKey);
        });
    }

    const keysStr = currentKeys.join('|');
    const prevKeysStr = state.renderedChartKeys.join('|');

    // If structure hasn't changed, just update chart data + non-numeric values
    if (keysStr === prevKeysStr && state.renderedChartKeys.length > 0) {
        for (const chartKey of currentKeys) {
            const chart = state.groupCharts[chartKey];
            if (chart) {
                const history = state.sensorHistory[chartKey];
                if (history) {
                    chart.data.labels = history.map(p => new Date(p.time).toLocaleTimeString());
                    chart.data.datasets[0].data = history.map(p => p.value);
                    chart.update('none');
                }
            } else {
                // Non-numeric: update the display element
                const safeId = 'nn-val-' + chartKey.replace(/[^a-zA-Z0-9]/g, '-');
                const el = document.getElementById(safeId);
                if (el) {
                    const history = state.sensorHistory[chartKey];
                    if (history && history.length > 0) {
                        el.textContent = history[history.length - 1].value;
                    }
                }

                const chartDomId = chartKey.replace(/[^a-zA-Z0-9]/g, '-');
                const cardEl = document.getElementById('nn-card-' + chartDomId);
                const safeRangeEl = document.getElementById('nn-safe-' + chartDomId);
                const range = getSafeRangeConfig(chartKey.replace('::', '/'));
                const history = state.sensorHistory[chartKey];
                const latestValue = history && history.length > 0 ? history[history.length - 1].value : undefined;
                const isUnsafe = isSafeRangeBreached(range, latestValue);
                if (cardEl) {
                    cardEl.classList.toggle('alarm-card', isUnsafe);
                }
                if (safeRangeEl) {
                    safeRangeEl.textContent = range ? formatSafeRangeSummary(range) : 'No safe threshold configured';
                    safeRangeEl.style.color = isUnsafe ? 'var(--danger)' : 'var(--text-tertiary)';
                }

                // Update non-numeric history list
                const histId = 'nn-hist-' + chartKey.replace(/[^a-zA-Z0-9]/g, '-');
                const histEl = document.getElementById(histId);
                if (histEl) {
                    if (history) {
                        histEl.innerHTML = history.slice(-8).reverse().map(p =>
                            `<div class="nn-history-item"><span class="nn-hist-val">${p.value}</span><span class="nn-hist-time">${new Date(p.time).toLocaleTimeString()}</span></div>`
                        ).join('');
                    }
                }
            }
        }
        return;
    }

    // Structure changed — rebuild DOM
    Object.values(state.groupCharts).forEach(c => { if (c && c.destroy) c.destroy(); });
    state.groupCharts = {};

    let html = '<div class="sensor-groups-flex">';
    for (const [groupName, group] of groupEntries) {
        const sensorCount = group.sensors.length;
        const sizeClass = sensorCount <= 2 ? 'group-md' : 'group-full';
        html += `<div class="glass p-3 sensor-group-item ${sizeClass}">
            <div class="d-flex align-items-center justify-content-between mb-2">
                <div class="group-label mb-0"><i class="bi ${group.icon}" style="color:${group.color};"></i> ${groupName}</div>
            </div>
            <div class="row g-3">`;

        const colClass = sensorCount <= 2 ? 'col-12' : 'col-md-6';

        group.sensors.forEach(sensor => {
            const chartKey = (sensor.origin || groupName) + '::' + sensor.name;
            const safeId = chartKey.replace(/[^a-zA-Z0-9]/g, '-');
            const isNumeric = typeof sensor.value === 'number';
            const history = state.sensorHistory[chartKey] || [];
            const safeRange = getSafeRangeConfig((sensor.origin || groupName) + '/' + sensor.name);
            const safeSummary = safeRange ? formatSafeRangeSummary(safeRange) : 'No safe threshold configured';
            const isUnsafe = isSafeRangeBreached(safeRange, sensor.value);

            const chartOriginLabel = sensor.origin
                ? `<div class="sensor-origin" style="font-size:.6rem;color:var(--text-tertiary);font-family:'JetBrains Mono',monospace;margin-bottom:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;" title="${sensor.origin}">${sensor.origin}</div>`
                : '';

            if (isNumeric) {
                // Numeric sensor: render a chart canvas
                const canvasId = 'gchart-' + safeId;
                html += `
                <div class="${colClass}">
                    <div class="glass-inner p-3 chart-in-group">
                        <div class="d-flex justify-content-between align-items-center mb-2">
                            <span class="sensor-name">${sensor.name.toUpperCase()}</span>
                            <span class="chart-live-value" style="font-family:'JetBrains Mono',monospace;font-size:.85rem;font-weight:600;color:var(--text-primary);">${sensor.value}<span class="sensor-unit">${sensor.unit || ''}</span></span>
                        </div>
                        ${chartOriginLabel}
                        <div style="position:relative;height:160px;">
                            <canvas id="${canvasId}"></canvas>
                        </div>
                    </div>
                </div>`;
            } else {
                // Non-numeric sensor: render a state display with history
                const recentHistory = history.slice(-8).reverse();
                let historyHtml = recentHistory.map(p =>
                    `<div class="nn-history-item"><span class="nn-hist-val">${p.value}</span><span class="nn-hist-time">${new Date(p.time).toLocaleTimeString()}</span></div>`
                ).join('');
                if (recentHistory.length === 0) {
                    historyHtml = '<div style="font-size:.72rem;color:var(--text-tertiary);">No history yet</div>';
                }
                html += `
                <div class="${colClass}">
                    <div id="nn-card-${safeId}" class="glass-inner p-3 chart-in-group non-numeric-display ${isUnsafe ? 'alarm-card' : ''}">
                        <div class="d-flex justify-content-between align-items-center mb-2">
                            <span class="sensor-name">${sensor.name.toUpperCase()}</span>
                            <span class="status-dot-sm ${sensor.status || 'ok'}" title="${sensor.status || 'ok'}"></span>
                        </div>
                        ${chartOriginLabel}
                        <div class="nn-current-value" id="nn-val-${safeId}">${sensor.value}</div>
                        <div id="nn-safe-${safeId}" style="font-size:.68rem;color:${isUnsafe ? 'var(--danger)' : 'var(--text-tertiary)'};margin-bottom:.5rem;">${safeSummary}</div>
                        <div class="nn-label">State History</div>
                        <div class="nn-history-list" id="nn-hist-${safeId}">${historyHtml}</div>
                        <div class="sensor-time mt-2"><i class="bi bi-clock" style="font-size:.55rem;"></i> ${relativeTime(sensor.lastUpdate)}</div>
                    </div>
                </div>`;
            }
        });

        html += '</div></div>';
    }
    html += '</div>';
    container.innerHTML = html;

    // Now create Chart.js instances for numeric sensors
    for (const [groupName, group] of groupEntries) {
        group.sensors.forEach(sensor => {
            const chartKey = (sensor.origin || groupName) + '::' + sensor.name;
            if (typeof sensor.value !== 'number') return;

            const safeId = chartKey.replace(/[^a-zA-Z0-9]/g, '-');
            const canvasId = 'gchart-' + safeId;
            const canvasEl = document.getElementById(canvasId);
            if (!canvasEl) return;

            const color = nextColor();
            const ctx = canvasEl.getContext('2d');
            const gradient = ctx.createLinearGradient(0, 0, 0, 160);
            gradient.addColorStop(0, color + '33');
            gradient.addColorStop(1, color + '00');

            const history = state.sensorHistory[chartKey] || [];

            state.groupCharts[chartKey] = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: history.map(p => new Date(p.time).toLocaleTimeString()),
                    datasets: [{
                        label: sensor.name + (sensor.unit ? ' (' + sensor.unit + ')' : ''),
                        data: history.map(p => p.value),
                        borderColor: color,
                        backgroundColor: gradient,
                        fill: true,
                        tension: 0.4,
                        pointRadius: 0,
                        pointHitRadius: 8,
                        borderWidth: 2
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    animation: { duration: 150 },
                    interaction: { intersect: false, mode: 'index' },
                    scales: {
                        x: {
                            ticks: { color: '#64748b', font: { family: "'JetBrains Mono', monospace", size: 9 }, maxTicksLimit: 6 },
                            grid: { color: 'rgba(99,130,191,.06)' }
                        },
                        y: {
                            ticks: { color: '#64748b', font: { family: "'JetBrains Mono', monospace", size: 9 } },
                            grid: { color: 'rgba(99,130,191,.06)' }
                        }
                    },
                    plugins: {
                        legend: { display: false },
                        tooltip: {
                            backgroundColor: '#0c1019',
                            borderColor: 'rgba(99,130,191,.15)',
                            borderWidth: 1,
                            titleFont: { family: "'Inter', sans-serif" },
                            bodyFont: { family: "'JetBrains Mono', monospace" }
                        }
                    }
                }
            });
        });
    }

    state.renderedChartKeys = currentKeys;
}

// ==========================================
// 6. ACTUATOR CONTROLS
// ==========================================
function renderActuators() {
    const container = document.getElementById('actuators-container');
    let html = '';

    for (const [id, act] of Object.entries(state.actuators)) {
        const isOn = act.state === 'ON' || act.state === true;
        const isPending = act.pending === true;

        let metricsHtml = '';
        if (act.metrics && act.metrics.length > 0) {
            metricsHtml = '<div class="mt-2" style="text-align:left;">';
            act.metrics.forEach(m => {
                metricsHtml += `<div style="font-family:'JetBrains Mono',monospace;font-size:.73rem;color:var(--text-secondary);padding:2px 0;">
                    ${m.name}: <span style="color:var(--text-primary);font-weight:600;">${m.value}</span>
                    <span style="font-size:.63rem;">${m.unit || ''}</span>
                </div>`;
            });
            metricsHtml += '</div>';
        }

        // US10: Related automations for this actuator
        const relatedRules = rules.filter(r => r.actuator === id);
        let rulesHtml = '';
        if (relatedRules.length > 0) {
            rulesHtml = '<div class="mt-2 pt-2" style="border-top:1px solid var(--border);text-align:left;">';
            rulesHtml += '<div style="font-size:.6rem;font-weight:700;color:var(--text-tertiary);text-transform:uppercase;letter-spacing:.08em;margin-bottom:.3rem;"><i class="bi bi-cpu" style="color:#c084fc;"></i> Automations</div>';
            relatedRules.forEach(r => {
                rulesHtml += `<div style="font-family:'JetBrains Mono',monospace;font-size:.63rem;color:var(--text-secondary);padding:2px 0;">
                    <span style="color:var(--success);">●</span> ${r.text}
                </div>`;
            });
            rulesHtml += '</div>';
        }

        const pendingBadge = isPending
            ? '<span style="font-size:.65rem;color:var(--warning);margin-left:6px;"><i class="bi bi-hourglass-split"></i> pending</span>'
            : '';

        html += `
        <div class="col-sm-6 col-md-4 col-lg-3">
            <div class="glass actuator-card">
                <div class="card-body">
                    <div class="sensor-name mb-2">${id}</div>
                    <div class="actuator-status mb-2" style="color:${isOn ? 'var(--success)' : 'var(--text-tertiary)'};">
                        <span class="status-dot-sm ${isOn ? 'ok' : ''}" style="margin-right:6px;"></span>${isOn ? 'ON' : 'OFF'}${pendingBadge}
                    </div>
                    ${metricsHtml}
                    <div class="toggle-group w-100 mt-2">
                        <button class="toggle-btn ${isOn ? 'active-on' : ''}" style="flex:1;" onclick="toggleActuator('${id}','ON')"${isPending ? ' disabled' : ''}>ON</button>
                        <button class="toggle-btn ${!isOn ? 'active-off' : ''}" style="flex:1;" onclick="toggleActuator('${id}','OFF')"${isPending ? ' disabled' : ''}>OFF</button>
                    </div>
                    ${rulesHtml}
                    <div class="sensor-time mt-2">
                        ${act.lastUpdate ? relativeTime(act.lastUpdate) : '—'}
                    </div>
                </div>
            </div>
        </div>`;
    }
    container.innerHTML = html || `
        <div class="col-12">
            <div class="glass p-4">
                <div class="empty-state">
                    <i class="bi bi-toggles2"></i>
                    <p>No actuators discovered yet</p>
                    <p class="empty-hint">Actuators will appear here automatically when detected on the network</p>
                </div>
            </div>
        </div>`;
}

function toggleActuator(actuatorId, action) {
    const command = { actuatorId, action, user: 'HabitatOperator' };
    stompClient.publish({ destination: '/app/actuators/control', body: JSON.stringify(command) });
    addLog('command', `Actuator ${actuatorId} → ${action}`);
    showToast(`${actuatorId} → ${action}`, 'info');
}

// ==========================================
// 7. RULE ENGINE
// ==========================================
// Rules are stored locally for display purposes.
// Backend (automation-evaluator) evaluates rules from database and triggers actions.
const rules = [];
const NUMERIC_RULE_OPERATORS = [
    { value: '>', label: '> greater than' },
    { value: '<', label: '< less than' },
    { value: '>=', label: '>= greater or equal' },
    { value: '<=', label: '<= less or equal' },
    { value: '==', label: '= equal to' },
    { value: '!=', label: '!= not equal' }
];
const TEXT_RULE_OPERATORS = [
    { value: '==', label: '= equal to' }
];

function normalizeSafeStateValue(value) {
    if (value === undefined || value === null) return null;
    const normalized = String(value).trim().toLowerCase();
    return normalized || null;
}

function normalizeSafeRange(range) {
    if (!range || typeof range !== 'object') return null;

    if (Array.isArray(range.allowedValues)) {
        const allowedValues = range.allowedValues
            .map(normalizeSafeStateValue)
            .filter(Boolean);
        return allowedValues.length > 0 ? { kind: 'enum', allowedValues } : null;
    }

    if (Number.isFinite(Number(range.min)) && Number.isFinite(Number(range.max))) {
        return {
            kind: 'numeric',
            min: Number(range.min),
            max: Number(range.max)
        };
    }

    return null;
}

function getSafeRangeConfig(sensorKey) {
    return normalizeSafeRange(state.safeRanges[sensorKey]);
}

function isSafeRangeNumericMetric(sensorKey) {
    const sensor = state.sensors[sensorKey];
    return typeof sensor?.value === 'number' && Number.isFinite(sensor.value);
}

function isSafeRangeBreached(range, value) {
    if (!range) return false;

    if (range.kind === 'numeric') {
        return typeof value === 'number' && (value < range.min || value > range.max);
    }

    const normalizedValue = normalizeSafeStateValue(value);
    return normalizedValue !== null && !range.allowedValues.includes(normalizedValue);
}

function formatSafeRangeSummary(range, unit = '', preferredUnit = unit, originalUnit = unit, hasConversion = false) {
    if (!range) return '';

    if (range.kind === 'enum') {
        return `Safe states: ${range.allowedValues.join(', ')}`;
    }

    let min = range.min;
    let max = range.max;
    let displayUnit = unit || '';
    if (hasConversion && preferredUnit !== originalUnit) {
        min = formatConverted(convertUnit(range.min, originalUnit, preferredUnit));
        max = formatConverted(convertUnit(range.max, originalUnit, preferredUnit));
        displayUnit = preferredUnit;
    }

    return `Range: ${min} - ${max} ${displayUnit}`.trim();
}

function ruleKey(rule) {
    if (rule.id !== undefined && rule.id !== null) {
        return `id:${rule.id}`;
    }
    return [rule.sensor, rule.metric, rule.operator, rule.threshold, rule.actuator, rule.action].join('|');
}

function upsertRule(localRule) {
    const key = ruleKey(localRule);
    const idx = rules.findIndex(r => ruleKey(r) === key);
    if (idx >= 0) {
        rules[idx] = { ...rules[idx], ...localRule };
    } else {
        rules.push(localRule);
    }
}

function handleRuleSnapshot(ruleData) {
    const sensor = ruleData?.sensorName || ruleData?.sensor;
    const metric = ruleData?.metricName || ruleData?.metric;
    const operator = ruleData?.operator;
    const thresholdRaw = ruleData?.valueText ?? ruleData?.value ?? ruleData?.threshold;
    const actuator = ruleData?.actuatorName || ruleData?.actuator;
    const action = ruleData?.actuatorState || ruleData?.action;

    if (!sensor || !operator || thresholdRaw === undefined || thresholdRaw === null || !actuator || !action) return;

    const threshold = String(thresholdRaw).trim();
    if (!threshold) return;

    const mappedRule = {
        id: ruleData?.id ?? null,
        name: `IF ${sensor.toUpperCase()}[${metric || '?'}] ${operator} ${threshold} THEN ${actuator} ${action}`,
        sensor,
        metric,
        operator,
        threshold,
        actuator,
        action,
        text: `IF ${sensor.toUpperCase()}[${metric || '?'}] ${operator} ${threshold} THEN ${actuator} ${action}`,
        enabled: true,
        createdAt: new Date().toISOString()
    };

    upsertRule(mappedRule);
    renderRules();
    updateStatsBadges();
}

function getPrimaryMetricInfo(sensorId) {
    const sensor = state.sensorData[sensorId];
    if (!sensor || !sensor.metrics) return null;

    const entries = Object.entries(sensor.metrics);
    if (entries.length === 0) return null;

    const [metricName, metric] = entries[0];
    return {
        metricName,
        value: metric?.value,
        unit: metric?.unit || ''
    };
}

function getMetricInfo(sensorId, metricName) {
    const sensor = state.sensorData[sensorId];
    if (!sensor || !sensor.metrics) return null;
    
    const metric = sensor.metrics[metricName];
    if (!metric) return null;
    
    return {
        metricName,
        value: metric?.value,
        unit: metric?.unit || ''
    };
}

function getAllMetricsForSensor(sensorId) {
    const sensor = state.sensorData[sensorId];
    if (!sensor || !sensor.metrics) return [];
    
    return Object.keys(sensor.metrics);
}

function isRuleMetricNumeric(primaryMetric) {
    return typeof primaryMetric?.value === 'number' && Number.isFinite(primaryMetric.value);
}

function renderRuleOperatorOptions(options, preferredValue) {
    const operatorSelect = document.getElementById('rule-operator');
    if (!operatorSelect) return;

    operatorSelect.innerHTML = options.map(option =>
        `<option value="${option.value}">${option.label}</option>`
    ).join('');

    const hasPreferredValue = options.some(option => option.value === preferredValue);
    operatorSelect.value = hasPreferredValue ? preferredValue : options[0].value;
}

function configureRuleBuilderForMetric(primaryMetric) {
    const thresholdInput = document.getElementById('rule-threshold');
    const unitBadge = document.getElementById('rule-unit-badge');
    const operatorSelect = document.getElementById('rule-operator');
    if (!thresholdInput || !unitBadge || !operatorSelect) return;

    const previousOperator = operatorSelect.value;
    const numericMetric = isRuleMetricNumeric(primaryMetric);
    renderRuleOperatorOptions(numericMetric ? NUMERIC_RULE_OPERATORS : TEXT_RULE_OPERATORS, previousOperator);

    thresholdInput.value = '';
    if (numericMetric) {
        thresholdInput.inputMode = 'decimal';
        thresholdInput.placeholder = '0';
        thresholdInput.setAttribute('spellcheck', 'false');
        unitBadge.textContent = primaryMetric?.unit || '—';
        return;
    }

    thresholdInput.inputMode = 'text';
    thresholdInput.placeholder = 'Enter enum value';
    thresholdInput.setAttribute('spellcheck', 'false');
    unitBadge.textContent = 'ENUM';
}

function populateRuleDropdowns() {
    const sensorSelect = document.getElementById('rule-sensor');
    const metricSelect = document.getElementById('rule-metric');
    const actuatorSelect = document.getElementById('rule-actuator');
    if (!sensorSelect || !metricSelect || !actuatorSelect) return;

    // Populate rule sensors
    const currentSensorVal = sensorSelect.value;
    sensorSelect.innerHTML = '<option value="" disabled>Select sensor…</option>';
    for (const [sensorId] of Object.entries(state.sensorData).sort(([a], [b]) => a.localeCompare(b))) {
        const metrics = getAllMetricsForSensor(sensorId);
        if (metrics.length === 0) continue;

        const label = `${sensorId} (${metrics.length} metric${metrics.length > 1 ? 's' : ''})`;
        const opt = document.createElement('option');
        opt.value = sensorId;
        opt.textContent = label;
        if (sensorId === currentSensorVal) opt.selected = true;
        sensorSelect.appendChild(opt);
    }

    // Also populate threshold sensor dropdown (US14)
    const thresholdSelect = document.getElementById('threshold-sensor');
    if (thresholdSelect) {
        const currentThreshVal = thresholdSelect.value;
        thresholdSelect.innerHTML = '<option value="" disabled>Select…</option>';
        for (const [key, sensor] of Object.entries(state.sensors).sort(([a], [b]) => a.localeCompare(b))) {
            const opt = document.createElement('option');
            opt.value = key;
            opt.textContent = key + (sensor.unit ? ` [${sensor.unit}]` : '');
            if (key === currentThreshVal) opt.selected = true;
            thresholdSelect.appendChild(opt);
        }

        configureSafeRangeForm();
    }

    const currentActVal = actuatorSelect.value;
    actuatorSelect.innerHTML = '<option value="" disabled>Select actuator…</option>';
    for (const [key, act] of Object.entries(state.actuators).sort ? Object.entries(state.actuators).sort(([a], [b]) => a.localeCompare(b)) : Object.entries(state.actuators)) {
        const label = key + (act ? ` [${act.state === 'ON' || act.state === true ? 'ON' : 'OFF'}]` : '');
        const opt = document.createElement('option');
        opt.value = key;
        opt.textContent = label;
        if (key === currentActVal) opt.selected = true;
        actuatorSelect.appendChild(opt);
    }

    // Populate metrics for selected sensor
    const selectedSensorId = sensorSelect.value;
    populateRuleMetrics(selectedSensorId);
}

function onRuleSensorChange() {
    const sensorId = document.getElementById('rule-sensor').value;
    populateRuleMetrics(sensorId);
}

function populateRuleMetrics(sensorId) {
    const metricSelect = document.getElementById('rule-metric');
    if (!metricSelect || !sensorId) {
        metricSelect.innerHTML = '<option value="" disabled>Select metric…</option>';
        configureRuleBuilderForMetric(null);
        return;
    }

    const metrics = getAllMetricsForSensor(sensorId);
    const currentMetricVal = metricSelect.value;
    
    metricSelect.innerHTML = '<option value="" disabled>Select metric…</option>';
    metrics.forEach(metricName => {
        const metric = state.sensorData[sensorId].metrics[metricName];
        const label = `${metricName} (${metric.value} ${metric.unit || ''})`;
        const opt = document.createElement('option');
        opt.value = metricName;
        opt.textContent = label;
        if (metricName === currentMetricVal) opt.selected = true;
        metricSelect.appendChild(opt);
    });
    
    // Auto-select first metric if none was selected
    if (!currentMetricVal && metrics.length > 0) {
        metricSelect.value = metrics[0];
    }
    
    onRuleMetricChange();
}

function onRuleMetricChange() {
    const sensorId = document.getElementById('rule-sensor').value;
    const metricName = document.getElementById('rule-metric').value;
    
    if (!sensorId || !metricName) {
        configureRuleBuilderForMetric(null);
        document.getElementById('rule-sensor-live').textContent = '—';
        return;
    }
    
    const metricInfo = getMetricInfo(sensorId, metricName);
    configureRuleBuilderForMetric(metricInfo);
    
    if (metricInfo) {
        document.getElementById('rule-sensor-live').textContent = `${metricInfo.value} ${metricInfo.unit || ''}`.trim();
    } else {
        document.getElementById('rule-sensor-live').textContent = '—';
    }
    
    updateRulePreview();
}

function refreshBuilderLiveValue() {
    const sensorId = document.getElementById('rule-sensor')?.value;
    const metricName = document.getElementById('rule-metric')?.value;
    if (!sensorId || !metricName) return;
    
    const metricInfo = getMetricInfo(sensorId, metricName);
    if (metricInfo) {
        const liveVal = document.getElementById('rule-sensor-live');
        if (liveVal) liveVal.textContent = `${metricInfo.value} ${metricInfo.unit || ''}`.trim();
    }
}

function updateRulePreview() {
    const sensor = document.getElementById('rule-sensor')?.value;
    const metric = document.getElementById('rule-metric')?.value;
    const op = document.getElementById('rule-operator')?.value;
    const threshold = document.getElementById('rule-threshold')?.value;
    const actuator = document.getElementById('rule-actuator')?.value;
    const action = document.getElementById('rule-action')?.value;
    const preview = document.getElementById('rule-preview');
    if (!preview) return;

    if (sensor && metric && actuator && threshold !== '') {
        preview.textContent = `IF ${sensor.toUpperCase()}[${metric}] ${op} ${threshold} THEN ${actuator} ${action}`;
        preview.style.color = 'var(--text-primary)';
    } else {
        preview.textContent = 'Select sensor, metric and actuator to preview rule…';
        preview.style.color = 'var(--text-tertiary)';
    }
}

function submitBuilderRule() {
    const name = document.getElementById('rule-name')?.value.trim();
    const sensor = document.getElementById('rule-sensor')?.value;
    const metric = document.getElementById('rule-metric')?.value;
    const operator = document.getElementById('rule-operator')?.value;
    const threshold = document.getElementById('rule-threshold')?.value.trim();
    const actuator = document.getElementById('rule-actuator')?.value;
    const action = document.getElementById('rule-action')?.value;
    const feedback = document.getElementById('rule-feedback');

    // US09: Enhanced validation
    if (!sensor) { showRuleFeedback(feedback, 'warning', 'Select a sensor.'); return; }
    if (!state.sensorData[sensor]) { showRuleFeedback(feedback, 'warning', 'Selected sensor not found in active sensors.'); return; }
    if (!metric) { showRuleFeedback(feedback, 'warning', 'Select a metric.'); return; }
    if (!actuator) { showRuleFeedback(feedback, 'warning', 'Select an actuator.'); return; }
    if (!state.actuators[actuator]) { showRuleFeedback(feedback, 'warning', 'Selected actuator not found in active actuators.'); return; }

    const selectedMetric = getMetricInfo(sensor, metric);
    if (!selectedMetric) { showRuleFeedback(feedback, 'warning', 'Selected metric not found.'); return; }

    const numericMetric = isRuleMetricNumeric(selectedMetric);
    if (threshold === '') {
        showRuleFeedback(feedback, 'warning', numericMetric ? 'Enter a valid numeric threshold.' : 'Enter the text value to match.');
        return;
    }

    if (numericMetric) {
        if (isNaN(Number(threshold))) {
            showRuleFeedback(feedback, 'warning', 'Enter a valid numeric threshold.');
            return;
        }
    } else if (operator !== '==') {
        showRuleFeedback(feedback, 'warning', 'Text-based sensors support only the EQUAL TO operator.');
        return;
    }

    // Check for duplicate rules
    const normalizedThreshold = threshold.trim().toLowerCase();
    const isDuplicate = rules.some(r =>
        r.sensor === sensor
        && r.metric === metric
        && r.operator === operator
        && String(r.threshold).trim().toLowerCase() === normalizedThreshold
        && r.actuator === actuator
        && r.action === action
    );
    if (isDuplicate) { showRuleFeedback(feedback, 'warning', 'An identical rule already exists.'); return; }

    const ruleText = `IF ${sensor.toUpperCase()}[${metric}] ${operator} ${threshold} THEN ${actuator} ${action}`;
    const rule = {
        id: Date.now(),
        name: name || ruleText,
        sensor,
        metric,
        operator,
        threshold,
        actuator,
        action,
        text: ruleText,
        enabled: true,
        createdAt: new Date().toISOString()
    };

    // Add rule to local state immediately for instant visual feedback
    upsertRule(rule);
    renderRules();
    updateStatsBadges();

    // Send rule to backend via message broker (newrules.topic)
    // Received by NewRulesListener in automation-evaluator
    stompClient.publish({
        destination: '/app/rules/add',
        body: JSON.stringify(rule)
    });

    showRuleFeedback(feedback, 'success', 'Rule created and sent to server...');
    showToast('Rule created: ' + ruleText, 'success');
    addLog('rule', 'New rule: ' + ruleText);
    resetRuleBuilder();
}

function showRuleFeedback(el, type, msg) {
    if (!el) return;
    const color = type === 'success' ? 'var(--success)' : 'var(--warning)';
    const icon = type === 'success' ? 'bi-check-circle' : 'bi-exclamation-triangle';
    el.innerHTML = `<span style="color:${color};"><i class="bi ${icon} me-1"></i>${msg}</span>`;
    setTimeout(() => { if (el) el.innerHTML = ''; }, 4000);
}

function resetRuleBuilder() {
    ['rule-name', 'rule-threshold'].forEach(id => {
        const el = document.getElementById(id); if (el) el.value = '';
    });
    ['rule-sensor', 'rule-metric', 'rule-actuator'].forEach(id => {
        const el = document.getElementById(id); if (el) el.selectedIndex = 0;
    });
    document.getElementById('rule-operator').selectedIndex = 0;
    document.getElementById('rule-action').selectedIndex = 0;
    document.getElementById('rule-unit-badge').textContent = '—';
    document.getElementById('rule-sensor-live').textContent = '—';
    updateRulePreview();
}

function renderRules() {
    const container = document.getElementById('active-rules-container');
    if (!container) return;

    if (rules.length === 0) {
        container.innerHTML = `
            <div class="empty-state" style="padding:2rem 1rem;">
                <i class="bi bi-cpu"></i>
                <p>No automation rules yet</p>
                <p class="empty-hint">Use the builder above to create IF/THEN rules based on discovered sensors</p>
            </div>`;
        return;
    }

    let html = '';
    rules.forEach((rule, idx) => {
        html += `
        <div class="rule-card">
            <div style="width:1.5rem;display:flex;align-items:center;justify-content:center;">
                <span class="status-dot-sm ok" style="margin:0;"></span>
            </div>
            <div class="rule-text">
                <span class="rule-keyword if" style="font-size:.62rem;">IF</span>
                ${rule.sensor.toUpperCase()}[${rule.metric || '?'}] ${rule.operator} ${rule.threshold}
                <span class="rule-keyword then" style="font-size:.62rem;margin-left:.5rem;">THEN</span>
                ${rule.actuator} ${rule.action}
            </div>
            <div class="rule-meta">${rule.name !== rule.text ? rule.name : ''}</div>
            <button class="btn btn-sm p-0" style="color:var(--danger);font-size:.9rem;" onclick="deleteRule(${idx})" title="Delete">
                <i class="bi bi-trash3"></i>
            </button>
        </div>`;
    });
    container.innerHTML = html;
}

function deleteRule(idx) {
    if (rules[idx]) {
        const rule = rules[idx];
        const name = rule.name;

        // Send delete request to backend (newrules.topic with deletionReq=true)
        stompClient.publish({
            destination: '/app/rules/delete',
            body: JSON.stringify({
                sensor: rule.sensor,
                metric: rule.metric,
                operator: rule.operator,
                threshold: rule.threshold,
                actuator: rule.actuator,
                action: rule.action
            })
        });

        rules.splice(idx, 1);
        renderRules();
        updateStatsBadges();
        addLog('rule', `Rule "${name}" deleted`);
        showToast(`Rule deleted: ${name}`, 'info');
    }
}

['rule-sensor', 'rule-metric', 'rule-operator', 'rule-threshold', 'rule-actuator', 'rule-action'].forEach(id => {
    document.getElementById(id)?.addEventListener('change', updateRulePreview);
    document.getElementById(id)?.addEventListener('input', updateRulePreview);
});
document.getElementById('rule-sensor')?.addEventListener('change', onRuleSensorChange);
document.getElementById('rule-metric')?.addEventListener('change', onRuleMetricChange);

// ==========================================
// 8. ACTIVITY LOG (US13: with filtering)
// ==========================================
const LOG_ICONS = {
    alert: 'bi-exclamation-triangle-fill',
    command: 'bi-terminal-fill',
    rule: 'bi-cpu',
    config: 'bi-broadcast'
};
const LOG_COLORS = {
    alert: 'var(--warning)',
    command: 'var(--accent)',
    rule: '#c084fc',
    config: 'var(--teal)'
};

function addLog(type, message) {
    const now = new Date().toLocaleTimeString();
    state.logs.unshift({ type, message, time: now });
    if (state.logs.length > 200) state.logs.pop();
    renderFilteredLogs();
}

function renderFilteredLogs() {
    const container = document.getElementById('system-logs');
    if (!container) return;
    container.innerHTML = '';

    const filtered = state.logFilter === 'all'
        ? state.logs
        : state.logs.filter(l => l.type === state.logFilter);

    if (filtered.length === 0) {
        container.innerHTML = '<div class="empty-state" style="padding:1.5rem;"><p style="font-size:.8rem;">No log entries for this filter</p></div>';
        return;
    }

    filtered.forEach(log => {
        const div = document.createElement('div');
        div.className = 'log-item';
        div.innerHTML = `<i class="bi ${LOG_ICONS[log.type] || 'bi-info-circle'}" style="color:${LOG_COLORS[log.type] || 'var(--text-tertiary)'};margin-top:2px;font-size:.85rem;"></i>
            <span class="log-time">${log.time}</span>
            <span>${log.message}</span>`;
        container.appendChild(div);
    });
}

function setLogFilter(filter, btn) {
    state.logFilter = filter;
    document.querySelectorAll('#log-filter-tabs .btn').forEach(b => b.classList.remove('active'));
    if (btn) btn.classList.add('active');
    renderFilteredLogs();
}

function downloadLogs(format) {
    // Get filtered logs based on current filter
    const filtered = state.logFilter === 'all'
        ? state.logs
        : state.logs.filter(l => l.type === state.logFilter);

    if (filtered.length === 0) {
        showToast('No logs to download', 'warning');
        return;
    }

    let content, filename, mimeType;

    if (format === 'json') {
        content = JSON.stringify(filtered, null, 2);
        filename = `activity-logs-${new Date().toISOString().split('T')[0]}.json`;
        mimeType = 'application/json';
    } else if (format === 'csv') {
        // CSV header
        const headers = ['Time', 'Type', 'Message'];
        const rows = [headers.join(',')];

        // Add data rows
        filtered.forEach(log => {
            const row = [
                log.time,
                log.type,
                `"${(log.message || '').replace(/"/g, '""')}"` // Escape quotes
            ].join(',');
            rows.push(row);
        });

        content = rows.join('\n');
        filename = `activity-logs-${new Date().toISOString().split('T')[0]}.csv`;
        mimeType = 'text/csv';
    }

    // Create blob and trigger download
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);

    addLog('config', `Logs exported as ${format.toUpperCase()}`);
    showToast(`Logs downloaded: ${filename}`, 'success');
}

// ==========================================
// 9. STATS & BADGES
// ==========================================
function updateStatsBadges() {
    const counts = {
        sensors: state.knownSensors.size,
        actuators: state.knownActuators.size,
        rules: rules.length,
        metrics: Object.keys(state.sensors).length,
        alarms: Object.keys(state.safeRanges).length
    };

    const idMap = {
        'stat-sensors': counts.sensors,
        'stat-actuators': counts.actuators,
        'stat-rules': counts.rules,
        'stat-metrics': counts.metrics,
        'badge-sensors': counts.sensors,
        'badge-actuators': counts.actuators,
        'badge-rules': counts.rules,
        'badge-alarms': counts.alarms
    };

    for (const [id, value] of Object.entries(idMap)) {
        const el = document.getElementById(id);
        if (el) el.textContent = value;
    }
}

// ==========================================
// 10. NAVIGATION
// ==========================================
document.querySelectorAll('[data-section]').forEach(btn => {
    btn.addEventListener('click', (e) => {
        e.preventDefault();
        const target = btn.dataset.section;
        document.querySelectorAll('.section').forEach(s => s.classList.add('d-none'));
        document.getElementById(target)?.classList.remove('d-none');
        document.querySelectorAll('.nav-link').forEach(n => n.classList.remove('active'));
        btn.classList.add('active');

        if (target === 'actuators') renderActuators();
        if (target === 'automations') { populateRuleDropdowns(); renderRules(); }
        if (target === 'network') renderNetworkHealth();
        if (target === 'safe-ranges') { populateRuleDropdowns(); renderSafeRanges(); }
        if (target === 'activity') renderFilteredLogs();
    });
});

function updateClock() {
    const el = document.getElementById('clock');
    if (el) el.textContent = new Date().toLocaleTimeString();
}
setInterval(updateClock, 1000);
updateClock();

// ==========================================
// 11. GROUPING TOGGLE (US06)
// ==========================================
function setGroupBy(mode) {
    state.groupBy = mode;
    // Destroy grouped charts since group structure changes
    Object.values(state.groupCharts).forEach(c => { if (c && c.destroy) c.destroy(); });
    state.groupCharts = {};
    state.renderedChartKeys = [];
    document.getElementById('group-origin')?.classList.toggle('active', mode === 'origin');
    document.getElementById('group-type')?.classList.toggle('active', mode === 'type');
    renderDashboard();
}

// ==========================================
// 12. FORCE REFRESH (US07)
// ==========================================
function forceRefreshSensor(sensorId) {
    stompClient.publish({
        destination: '/app/sensors/refresh',
        body: JSON.stringify({ sensorId })
    });
    addLog('command', `Force refresh requested: ${sensorId}`);
    showToast(`Refresh requested: ${sensorId}`, 'info');
}

// ==========================================
// 13. NETWORK HEALTH (US11)
// ==========================================
function renderNetworkHealth() {
    const statsContainer = document.getElementById('network-stats-cards');
    const freshnessContainer = document.getElementById('sensor-freshness-container');
    if (!statsContainer) return;

    const isConnected = stompClient.connected;
    const msgRate = state.networkStats.messageTimestamps.length;
    const lastMsg = state.networkStats.lastMessageTime
        ? relativeTime(new Date(state.networkStats.lastMessageTime).toISOString())
        : 'N/A';

    const now = Date.now();
    let staleSensors = 0;
    for (const [, sData] of Object.entries(state.sensorData)) {
        if (now - new Date(sData.lastUpdate).getTime() > 30000) staleSensors++;
    }

    statsContainer.innerHTML = `
        <div class="col-sm-6 col-md-3">
            <div class="glass p-3 text-center">
                <div class="sensor-name">Broker</div>
                <div class="sensor-value mt-1" style="font-size:1.1rem;color:${isConnected ? 'var(--success)' : 'var(--danger)'};">
                    <i class="bi ${isConnected ? 'bi-check-circle-fill' : 'bi-x-circle-fill'}"></i>
                    ${isConnected ? 'Connected' : 'Disconnected'}
                </div>
            </div>
        </div>
        <div class="col-sm-6 col-md-3">
            <div class="glass p-3 text-center">
                <div class="sensor-name">Messages / min</div>
                <div class="sensor-value mt-1" style="font-size:1.4rem;">${msgRate}</div>
            </div>
        </div>
        <div class="col-sm-6 col-md-3">
            <div class="glass p-3 text-center">
                <div class="sensor-name">Last Message</div>
                <div class="sensor-value mt-1" style="font-size:1.1rem;">${lastMsg}</div>
            </div>
        </div>
        <div class="col-sm-6 col-md-3">
            <div class="glass p-3 text-center">
                <div class="sensor-name">Stale Sensors</div>
                <div class="sensor-value mt-1" style="font-size:1.4rem;color:${staleSensors > 0 ? 'var(--warning)' : 'var(--success)'};">
                    ${staleSensors} / ${state.knownSensors.size}
                </div>
            </div>
        </div>`;

    if (!freshnessContainer) return;
    if (Object.keys(state.sensorData).length === 0) {
        freshnessContainer.innerHTML = '<div class="empty-state" style="padding:1rem;"><p>No sensors discovered yet</p></div>';
        return;
    }

    let fhtml = '<div class="row g-2">';
    for (const [sensorId, sData] of Object.entries(state.sensorData).sort(([a], [b]) => a.localeCompare(b))) {
        const age = (now - new Date(sData.lastUpdate).getTime()) / 1000;
        let freshClass = 'ok';
        if (age > 30) freshClass = 'warning';
        if (age > 120) freshClass = 'error';

        fhtml += `
        <div class="col-sm-6 col-md-4 col-lg-3">
            <div class="glass-inner p-2 d-flex align-items-center gap-2">
                <span class="status-dot-sm ${freshClass}"></span>
                <span style="font-size:.75rem;font-weight:600;flex:1;">${sensorId}</span>
                <span style="font-family:'JetBrains Mono',monospace;font-size:.68rem;color:var(--text-tertiary);">${relativeTime(sData.lastUpdate)}</span>
            </div>
        </div>`;
    }
    fhtml += '</div>';
    freshnessContainer.innerHTML = fhtml;
}

// ==========================================
// 14. DATA EXPORT (US12)
// ==========================================
function exportData(format) {
    const data = [];
    for (const [chartKey, history] of Object.entries(state.sensorHistory)) {
        const parts = chartKey.split('::');
        const sensorId = parts[0];
        const metricName = parts[1];
        history.forEach(point => {
            data.push({
                sensor: sensorId,
                metric: metricName,
                value: point.value,
                timestamp: point.time
            });
        });
    }

    if (data.length === 0) {
        showToast('No data to export yet', 'warning');
        return;
    }

    if (format === 'json') {
        downloadFile(JSON.stringify(data, null, 2), 'sensor_data.json', 'application/json');
    } else {
        let csv = 'sensor,metric,value,timestamp\n';
        data.forEach(d => {
            csv += `"${d.sensor}","${d.metric}",${d.value},"${d.timestamp}"\n`;
        });
        downloadFile(csv, 'sensor_data.csv', 'text/csv');
    }

    showToast(`Exported ${data.length} data points as ${format.toUpperCase()}`, 'success');
    addLog('command', `Data exported: ${data.length} points (${format.toUpperCase()})`);
}

function downloadFile(content, filename, mimeType) {
    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}

// ==========================================
// 15. SAFE RANGES (US14)
// ==========================================
function checkSafeRange(sensorKey, value) {
    const range = getSafeRangeConfig(sensorKey);
    if (!range) return;

    if (range.kind === 'numeric') {
        if (typeof value !== 'number') return;
        if (value < range.min || value > range.max) {
            addLog('alert', `\u26A0 ${sensorKey} = ${value} OUT OF RANGE [${range.min} - ${range.max}]`);
            showToast(`${sensorKey} = ${value} out of safe range!`, 'error');
        }
        return;
    }

    const normalizedValue = normalizeSafeStateValue(value);
    if (normalizedValue && !range.allowedValues.includes(normalizedValue)) {
        addLog('alert', `\u26A0 ${sensorKey} = ${value} NOT IN SAFE STATES [${range.allowedValues.join(', ')}]`);
        showToast(`${sensorKey} = ${value} is not in safe states`, 'error');
    }
}

function configureSafeRangeForm() {
    const sensorKey = document.getElementById('threshold-sensor')?.value;
    const minGroup = document.getElementById('threshold-min-group');
    const maxGroup = document.getElementById('threshold-max-group');
    const statesGroup = document.getElementById('threshold-states-group');
    const help = document.getElementById('threshold-help');
    const minInput = document.getElementById('threshold-min');
    const maxInput = document.getElementById('threshold-max');
    const statesInput = document.getElementById('threshold-safe-states');
    if (!minGroup || !maxGroup || !statesGroup || !help || !minInput || !maxInput || !statesInput) return;

    minInput.value = '';
    maxInput.value = '';
    statesInput.value = '';

    if (!sensorKey) {
        minGroup.classList.remove('d-none');
        maxGroup.classList.remove('d-none');
        statesGroup.classList.add('d-none');
        help.textContent = 'Select a metric to configure either a numeric interval or a list of safe states.';
        return;
    }

    const range = getSafeRangeConfig(sensorKey);
    if (isSafeRangeNumericMetric(sensorKey)) {
        minGroup.classList.remove('d-none');
        maxGroup.classList.remove('d-none');
        statesGroup.classList.add('d-none');
        help.textContent = 'Numeric metrics are safe only when the current value stays inside the configured min/max interval.';

        if (range?.kind === 'numeric') {
            minInput.value = range.min;
            maxInput.value = range.max;
        }
        return;
    }

    minGroup.classList.add('d-none');
    maxGroup.classList.add('d-none');
    statesGroup.classList.remove('d-none');
    help.textContent = 'Text metrics are safe only when the current value matches one of the comma-separated states.';

    if (range?.kind === 'enum') {
        statesInput.value = range.allowedValues.join(', ');
    }
}

function addSafeRange() {
    const sensorKey = document.getElementById('threshold-sensor')?.value;
    const min = document.getElementById('threshold-min')?.value;
    const max = document.getElementById('threshold-max')?.value;
    const safeStates = document.getElementById('threshold-safe-states')?.value || '';

    if (!sensorKey) { showToast('Select a sensor/metric', 'warning'); return; }

    if (isSafeRangeNumericMetric(sensorKey)) {
        if (min === '' || max === '' || isNaN(Number(min)) || isNaN(Number(max))) {
            showToast('Enter valid min and max values', 'warning'); return;
        }
        if (Number(min) >= Number(max)) {
            showToast('Min must be less than Max', 'warning'); return;
        }

        state.safeRanges[sensorKey] = { kind: 'numeric', min: Number(min), max: Number(max) };
        localStorage.setItem('safeRanges', JSON.stringify(state.safeRanges));
        addLog('config', `Safe range set: ${sensorKey} [${min} - ${max}]`);
        showToast(`Safe range set for ${sensorKey}`, 'success');
    } else {
        const allowedValues = safeStates
            .split(',')
            .map(normalizeSafeStateValue)
            .filter(Boolean);

        if (allowedValues.length === 0) {
            showToast('Enter at least one safe state', 'warning'); return;
        }

        state.safeRanges[sensorKey] = {
            kind: 'enum',
            allowedValues: [...new Set(allowedValues)]
        };
        localStorage.setItem('safeRanges', JSON.stringify(state.safeRanges));
        addLog('config', `Safe states set: ${sensorKey} [${state.safeRanges[sensorKey].allowedValues.join(', ')}]`);
        showToast(`Safe states set for ${sensorKey}`, 'success');
    }

    renderSafeRanges();
    updateStatsBadges();
    configureSafeRangeForm();
}

function deleteSafeRange(key) {
    delete state.safeRanges[key];
    localStorage.setItem('safeRanges', JSON.stringify(state.safeRanges));
    addLog('config', `Safe range removed: ${key}`);
    renderSafeRanges();
    updateStatsBadges();
}

function renderSafeRanges() {
    const container = document.getElementById('safe-ranges-container');
    if (!container) return;

    const entries = Object.entries(state.safeRanges);
    if (entries.length === 0) {
        container.innerHTML = '<div class="empty-state" style="padding:1.5rem;"><i class="bi bi-shield-check"></i><p>No safe thresholds configured</p><p class="empty-hint">Set either a numeric interval or a list of safe states to trigger visual alarms when a metric becomes unsafe</p></div>';
        return;
    }

    let html = '';
    entries.forEach(([key, range]) => {
        const sensor = state.sensors[key];
        const safeRange = normalizeSafeRange(range);
        if (!safeRange) return;
        const currentVal = sensor ? sensor.value : '\u2014';
        const unit = sensor ? (sensor.unit || '') : '';
        const isOutOfRange = sensor && isSafeRangeBreached(safeRange, sensor.value);
        const label = safeRange.kind === 'numeric' ? 'Range' : 'Safe states';
        const summary = safeRange.kind === 'numeric'
            ? `<span style="color:var(--success);">${safeRange.min}</span> - <span style="color:var(--success);">${safeRange.max}</span> ${unit}`
            : `<span style="color:var(--success);">${safeRange.allowedValues.join(', ')}</span>`;

        html += `
        <div class="rule-card ${isOutOfRange ? 'alarm-card' : ''}">
            <div style="flex:1;">
                <div style="font-family:'JetBrains Mono',monospace;font-size:.78rem;color:var(--text-primary);">
                    ${key}
                </div>
                <div style="font-size:.68rem;color:var(--text-tertiary);margin-top:2px;">
                    ${label}: ${summary}
                    &nbsp;|&nbsp; Current: <span style="color:${isOutOfRange ? 'var(--danger)' : 'var(--text-primary)'};font-weight:600;">${currentVal}</span> ${unit}
                </div>
            </div>
            ${isOutOfRange ? '<span class="alarm-indicator"><i class="bi bi-exclamation-triangle-fill"></i></span>' : ''}
            <button class="btn btn-sm p-0" style="color:var(--danger);font-size:.9rem;" onclick="deleteSafeRange('${key.replace(/'/g, "\\'")}')" title="Remove">
                <i class="bi bi-trash3"></i>
            </button>
        </div>`;
    });
    container.innerHTML = html;
}

// ==========================================
// 16. STARTUP
// ==========================================
renderRules();
updateStatsBadges();
Object.entries(state.safeRanges).forEach(([key, range]) => {
    const normalized = normalizeSafeRange(range);
    if (normalized) {
        state.safeRanges[key] = normalized;
    } else {
        delete state.safeRanges[key];
    }
});
localStorage.setItem('safeRanges', JSON.stringify(state.safeRanges));
renderSafeRanges();
configureSafeRangeForm();
stompClient.activate();

// Periodically refresh relative times and builder live values
setInterval(() => {
    refreshBuilderLiveValue();
    if (!document.getElementById('dashboard')?.classList.contains('d-none')) {
        renderDashboard();
    }
}, 5000);

document.getElementById('threshold-sensor')?.addEventListener('change', configureSafeRangeForm);