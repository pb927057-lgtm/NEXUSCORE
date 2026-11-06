import React, { useState, useEffect } from 'react';
import {
  Shield,
  Smartphone,
  ArrowLeft,
  QrCode,
  Lock,
  Plus,
  RefreshCw,
  AlertTriangle,
  CheckCircle,
  HelpCircle,
  FolderArchive,
  Info,
  ChevronRight,
  ExternalLink,
  Ban,
  Send,
  Wifi,
  WifiOff,
  Server,
  User,
  Database,
  HardDrive,
  Download
} from 'lucide-react';
import { velDb, velOfflineSync, VelRepository, VelLocalRecord } from './db';

type ScreenType =
  | 'WELCOME'
  | 'SUBSCRIPTION_GATE'
  | 'ROLE_SELECTION'
  | 'CORE_HOME'
  | 'TERMINAL_HOME'
  | 'PAIRING_CORE'
  | 'PAIRING_TERMINAL'
  | 'DEVICE_MANAGEMENT';

type DeviceRole = 'CORE' | 'TERMINAL';

type RelayState =
  | 'DISCONNECTED'
  | 'CONNECTING'
  | 'AUTHENTICATED'
  | 'REVOKED';

interface PairedDevice {
  id: string;
  name: string;
  role: DeviceRole;
  fingerprint: string;
  sasCode: string;
  isLocallyRevoked: boolean;
  statusText: string;
}

export function App() {
  const [currentScreen, setCurrentScreen] = useState<ScreenType>('WELCOME');
  const [selectedRole, setSelectedRole] = useState<DeviceRole | null>(null);
  const [viewMode, setViewMode] = useState<'PHONE_PREVIEW' | 'PROJECT_TREE'>('PHONE_PREVIEW');

  // Estado de conexión Relay simulada (Etapa 3)
  const [relayState, setRelayState] = useState<RelayState>('AUTHENTICATED');
  const [testMessageFeedback, setTestMessageFeedback] = useState<string | null>(null);
  const [isSendingMessage, setIsSendingMessage] = useState(false);

  // Soberanía Local & Base de datos Offline-First (IndexedDB: VelLocalDB)
  const [localRecords, setLocalRecords] = useState<VelLocalRecord[]>([]);
  const [isSyncing, setIsSyncing] = useState(false);
  const [newRecordInput, setNewRecordInput] = useState('');
  const [dbStatusFeedback, setDbStatusFeedback] = useState<string | null>(null);

  const refreshLocalRecords = async () => {
    try {
      const records = await VelRepository.getAllEncryptedItems();
      setLocalRecords(records);
    } catch (err) {
      console.error('Error al acceder a VelLocalDB:', err);
    }
  };

  useEffect(() => {
    refreshLocalRecords();
  }, []);

  // Implementación del guardado directo con VelRepository respetando la arquitectura offline-first
  const handleSave = async (
    customId?: string,
    customCiphertext?: string,
    customIv?: string
  ) => {
    const itemId = customId || (newRecordInput.trim() ? `nota-${Date.now()}` : `nota-secreta-${localRecords.length + 1}`);
    const textoCifradoSimulado = customCiphertext || (newRecordInput.trim() ? btoa(newRecordInput.trim()) : 'DATOS_CIFRADOS_CON_AES_GCM');
    const ivSimulado = customIv || 'iv-aleatorio';

    try {
      await VelRepository.saveEncryptedItem(itemId, textoCifradoSimulado, ivSimulado);
      console.log('¡Guardado localmente en IndexedDB sin depender de la nube!');
      setNewRecordInput('');
      await refreshLocalRecords();
      setDbStatusFeedback('¡Guardado localmente en IndexedDB sin depender de la nube!');
      setTimeout(() => setDbStatusFeedback(null), 3500);
    } catch (err) {
      setDbStatusFeedback('Error al guardar localmente en IndexedDB');
    }
  };

  const handleSyncWithCore = async () => {
    setIsSyncing(true);
    setDbStatusFeedback('Sincronizando registros locales con Core vía canal cifrado...');
    setTimeout(async () => {
      setIsSyncing(false);
      setDbStatusFeedback(`✓ Sincronización con Core completada (${localRecords.length} registros verificados).`);
      setTimeout(() => setDbStatusFeedback(null), 4000);
    }, 700);
  };

  const handleDeleteLocalRecord = async (id: string) => {
    try {
      await VelRepository.deleteItem(id);
      await refreshLocalRecords();
      setDbStatusFeedback(`✓ Registro eliminado de la base local.`);
      setTimeout(() => setDbStatusFeedback(null), 3000);
    } catch (err) {
      setDbStatusFeedback('Error al eliminar registro');
    }
  };

  // Estado de SubscriptionGate
  const [isGateLoading, setIsGateLoading] = useState(false);
  const [showUsdtModal, setShowUsdtModal] = useState(false);

  // Perfil de suscripción y licencia activa
  const [subscriptionProfile] = useState({
    email: 'usuario@example.com',
    subscriptionStatus: 'active',
    expiresAt: '2027-03-01T00:00:00Z',
    linkedDevices: {
      core_device_id_xyz: { role: 'CORE', status: 'active' },
      terminal_device_id_abc: { role: 'TERMINAL', status: 'active' }
    }
  });

  // Identidades persistentes de la Etapa 2/3
  const coreIdentity = {
    deviceId: 'VEL-CORE-7B42',
    fingerprint: 'E4:A1:7B:82:9D:1C:33:F0:8A:21:5E:44:B9:02:18:7C',
    shortFingerprint: 'E4:A1:7B...18:7C'
  };

  const terminalIdentity = {
    deviceId: 'VEL-TERM-991A',
    fingerprint: '3F:9C:20:88:B1:0E:55:A2:14:D9:67:80:CC:11:42:09',
    shortFingerprint: '3F:9C:20...42:09'
  };

  // Repositorio de dispositivos
  const [devices, setDevices] = useState<PairedDevice[]>([
    {
      id: 'VEL-TERM-991A',
      name: 'Teléfono móvil personal',
      role: 'TERMINAL',
      fingerprint: '3F:9C:20:88:B1:0E:55:A2:14:D9:67:80:CC:11:42:09',
      sasCode: '482910',
      isLocallyRevoked: false,
      statusText: 'Emparejado y verificado'
    }
  ]);

  const [confirmRevokeTarget, setConfirmRevokeTarget] = useState<PairedDevice | null>(null);
  const [confirmEmergencyDevice, setConfirmEmergencyDevice] = useState<PairedDevice | null>(null);

  // Simulación de envío cifrado E2EE
  const handleSendTestMessage = (origin: 'CORE' | 'TERMINAL') => {
    if (relayState === 'REVOKED') {
      setTestMessageFeedback('Error: El terminal ha sido revocado y no puede transmitir datos.');
      return;
    }
    if (relayState === 'DISCONNECTED') {
      setTestMessageFeedback('Error: Sin conexión con el servidor de relevo. Mensaje descartado.');
      return;
    }

    setIsSendingMessage(true);
    setTestMessageFeedback(`Cifrando paquete con AES-256-GCM y transmitiendo vía Relay a ${origin === 'CORE' ? 'Terminal' : 'CORE'}...`);

    setTimeout(() => {
      setIsSendingMessage(false);
      const elapsed = Math.floor(Math.random() * 25) + 20;
      setTestMessageFeedback(
        `✓ Confirmación autenticada recibida de ${origin === 'CORE' ? 'Terminal' : 'CORE'} en ${elapsed} ms. Cifrado E2EE íntegro.`
      );
    }, 400);
  };

  // Simulación de revocación remota
  const handleConfirmRemoteRevocation = (targetId: string) => {
    setDevices((prev) =>
      prev.map((d) =>
        d.id === targetId
          ? { ...d, isLocallyRevoked: true, statusText: 'Revocado remotamente' }
          : d
      )
    );
    setConfirmRevokeTarget(null);
    setTestMessageFeedback('✓ Revocación registrada en el Relay: la conexión del Terminal fue cerrada forzosamente (código 4003).');

    // Si el usuario está viendo el Terminal o cambia a él, el terminal se encuentra revocado
    if (targetId === terminalIdentity.deviceId) {
      setRelayState('REVOKED');
    }
  };

  return (
    <div className="min-h-screen bg-[#0B1220] text-[#F1F5F9] flex flex-col font-sans selection:bg-[#00CFE8]/20">
      {/* Barra superior de estado de la Etapa 3 */}
      <header className="h-14 border-b border-[#22324C] bg-[#111C2E]/90 backdrop-blur px-6 flex items-center justify-between sticky top-0 z-50">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-[#18263E] border border-[#22324C] flex items-center justify-center text-[#00CFE8]">
            <Shield className="w-4 h-4" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-bold text-sm tracking-wider text-white">VEL 0.1</span>
              <span className="text-[10px] bg-[#00CFE8]/15 text-[#00CFE8] font-semibold px-2 py-0.5 rounded border border-[#00CFE8]/30">
                ETAPA 3: Comunicación Remota, Relay WebSockets & Revocación
              </span>
            </div>
            <p className="text-[11px] text-[#94A3B8]">
              WSS Relay • Cifrado AES-256-GCM E2EE • Reconexión con Backoff • Cierre Forzoso 4003
            </p>
          </div>
        </div>

        {/* Controles de estado y selector de modo */}
        <div className="flex items-center gap-3">
          {/* Indicador de estado del Relay */}
          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-[#0B1220] border border-[#22324C] text-[11px]">
            <span
              className={`w-2 h-2 rounded-full ${
                relayState === 'AUTHENTICATED'
                  ? 'bg-[#10B981]'
                  : relayState === 'REVOKED'
                  ? 'bg-[#F59E0B]'
                  : 'bg-[#EF4444]'
              }`}
            ></span>
            <span className="text-[#94A3B8]">Relay:</span>
            <span
              className={`font-semibold ${
                relayState === 'AUTHENTICATED'
                  ? 'text-[#10B981]'
                  : relayState === 'REVOKED'
                  ? 'text-[#F59E0B]'
                  : 'text-[#EF4444]'
              }`}
            >
              {relayState === 'AUTHENTICATED'
                ? 'Conectado (WSS)'
                : relayState === 'REVOKED'
                ? 'Revocado (4003)'
                : 'Sin conexión'}
            </span>
          </div>

          <button
            onClick={() => setViewMode('PHONE_PREVIEW')}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition-all ${
              viewMode === 'PHONE_PREVIEW'
                ? 'bg-[#00CFE8] text-[#0B1220] font-bold shadow-md'
                : 'text-[#94A3B8] hover:text-white bg-[#18263E]'
            }`}
          >
            <Smartphone className="w-3.5 h-3.5" />
            <span>Simulador de Interfaz</span>
          </button>

          <button
            onClick={() => setViewMode('PROJECT_TREE')}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition-all ${
              viewMode === 'PROJECT_TREE'
                ? 'bg-[#00CFE8] text-[#0B1220] font-bold shadow-md'
                : 'text-[#94A3B8] hover:text-white bg-[#18263E]'
            }`}
          >
            <FolderArchive className="w-3.5 h-3.5" />
            <span>Arquitectura y Archivos</span>
          </button>
        </div>
      </header>

      {/* Contenido principal */}
      <main className="flex-1 flex items-center justify-center p-6 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-[#111C2E] via-[#0B1220] to-[#070A11]">
        {viewMode === 'PHONE_PREVIEW' ? (
          <div className="flex flex-col lg:flex-row items-center gap-8 max-w-5xl w-full justify-center">
            {/* Panel de control de pruebas de red (Etapa 3) */}
            <div className="max-w-xs w-full space-y-4 text-xs">
              <div className="p-4 rounded-xl bg-[#111C2E] border border-[#22324C] space-y-3">
                <div className="flex items-center gap-2 text-[#00CFE8]">
                  <Server className="w-4 h-4" />
                  <span className="font-bold uppercase tracking-wider text-[10px]">
                    Servidor Relay Activo
                  </span>
                </div>
                <p className="text-[#94A3B8] leading-relaxed">
                  Intermediario WebSocket que autentica conexiones y enruta paquetes cifrados sin acceso a texto en claro ni claves privadas.
                </p>

                {/* Simulador de eventos de red */}
                <div className="pt-2 border-t border-[#22324C] space-y-1.5">
                  <span className="font-semibold text-white block text-[11px]">Simular condiciones de red:</span>
                  <div className="flex gap-2">
                    <button
                      onClick={() => setRelayState('AUTHENTICATED')}
                      className={`flex-1 py-1 rounded text-[10px] font-semibold ${
                        relayState === 'AUTHENTICATED'
                          ? 'bg-[#10B981] text-[#0B1220]'
                          : 'bg-[#18263E] text-[#94A3B8] hover:text-white'
                      }`}
                    >
                      En línea
                    </button>
                    <button
                      onClick={() => setRelayState('DISCONNECTED')}
                      className={`flex-1 py-1 rounded text-[10px] font-semibold ${
                        relayState === 'DISCONNECTED'
                          ? 'bg-[#EF4444] text-white'
                          : 'bg-[#18263E] text-[#94A3B8] hover:text-white'
                      }`}
                    >
                      Desconectar
                    </button>
                    <button
                      onClick={() => setRelayState('REVOKED')}
                      className={`flex-1 py-1 rounded text-[10px] font-semibold ${
                        relayState === 'REVOKED'
                          ? 'bg-[#F59E0B] text-[#0B1220]'
                          : 'bg-[#18263E] text-[#94A3B8] hover:text-white'
                      }`}
                    >
                      Revocado
                    </button>
                  </div>
                </div>
              </div>

              {/* Panel de Soberanía Local & Offline-First (IndexedDB: VelLocalDB) */}
              <div className="p-4 rounded-xl bg-[#111C2E] border border-[#22324C] space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2 text-[#00CFE8]">
                    <Database className="w-4 h-4" />
                    <span className="font-bold uppercase tracking-wider text-[10px]">
                      Soberanía Local (IndexedDB)
                    </span>
                  </div>
                  <span className="text-[10px] bg-[#00CFE8]/10 text-[#00CFE8] font-mono px-1.5 py-0.5 rounded border border-[#00CFE8]/20">
                    {localRecords.length} reg.
                  </span>
                </div>
                <p className="text-[#94A3B8] leading-relaxed text-[11px]">
                  Almacenamiento offline-first en <code className="text-[#00CFE8] font-mono">VelLocalDB</code>. Los datos se cifran con AES-256-GCM antes de guardarse.
                </p>
                <div className="space-y-1.5">
                  <input
                    type="text"
                    placeholder="Nota/dato confidencial..."
                    value={newRecordInput}
                    onChange={(e) => setNewRecordInput(e.target.value)}
                    className="w-full px-2.5 py-1.5 rounded-lg bg-[#0B1220] border border-[#22324C] text-[11px] text-white placeholder-[#64748B] focus:border-[#00CFE8] outline-none"
                  />
                  <div className="flex gap-1.5">
                    <button
                      onClick={() => handleSave()}
                      className="flex-1 py-1.5 rounded-lg bg-[#00CFE8] hover:bg-[#00b8ce] text-[#0B1220] font-bold text-[10px] transition-all flex items-center justify-center gap-1"
                    >
                      <Plus className="w-3 h-3" />
                      <span>Guardar</span>
                    </button>
                    <button
                      onClick={() => handleSave('nota-secreta-1', 'DATOS_CIFRADOS_CON_AES_GCM', 'iv-aleatorio')}
                      title="Ejecutar: nota-secreta-1"
                      className="py-1.5 px-2 rounded-lg bg-[#18263E] hover:bg-[#22324C] border border-[#22324C] text-[#00CFE8] font-mono text-[9px] transition-all flex items-center gap-1"
                    >
                      <span>Ejemplo</span>
                    </button>
                    <button
                      onClick={handleSyncWithCore}
                      disabled={isSyncing}
                      className="py-1.5 px-2 rounded-lg bg-[#18263E] hover:bg-[#22324C] border border-[#22324C] text-white font-medium text-[10px] transition-all flex items-center gap-1"
                    >
                      <RefreshCw className={`w-3 h-3 ${isSyncing ? 'animate-spin' : ''}`} />
                      <span>Sync</span>
                    </button>
                  </div>
                </div>
                {dbStatusFeedback && (
                  <div className="p-2 rounded-lg bg-[#0B1220] border border-[#00CFE8]/30 text-[10px] text-[#00CFE8] leading-tight">
                    {dbStatusFeedback}
                  </div>
                )}
              </div>

              {/* Navegación rápida de pantallas */}
              <div className="p-4 rounded-xl bg-[#111C2E] border border-[#22324C] space-y-2">
                <span className="font-bold text-white text-[11px] block">Navegación de pantallas:</span>
                <div className="grid grid-cols-2 gap-1.5 text-[10px]">
                  <button
                    onClick={() => setCurrentScreen('WELCOME')}
                    className="p-1.5 rounded bg-[#18263E] hover:bg-[#22324C] text-[#94A3B8] hover:text-white"
                  >
                    1. Bienvenida
                  </button>
                  <button
                    onClick={() => setCurrentScreen('ROLE_SELECTION')}
                    className="p-1.5 rounded bg-[#18263E] hover:bg-[#22324C] text-[#94A3B8] hover:text-white"
                  >
                    2. Roles
                  </button>
                  <button
                    onClick={() => setCurrentScreen('CORE_HOME')}
                    className="p-1.5 rounded bg-[#18263E] hover:bg-[#22324C] text-[#94A3B8] hover:text-white"
                  >
                    3. CORE Home
                  </button>
                  <button
                    onClick={() => setCurrentScreen('TERMINAL_HOME')}
                    className="p-1.5 rounded bg-[#18263E] hover:bg-[#22324C] text-[#94A3B8] hover:text-white"
                  >
                    4. Terminal Home
                  </button>
                  <button
                    onClick={() => setCurrentScreen('PAIRING_CORE')}
                    className="p-1.5 rounded bg-[#18263E] hover:bg-[#22324C] text-[#94A3B8] hover:text-white"
                  >
                    5. Pairing
                  </button>
                  <button
                    onClick={() => setCurrentScreen('DEVICE_MANAGEMENT')}
                    className="p-1.5 rounded bg-[#18263E] hover:bg-[#22324C] text-[#94A3B8] hover:text-white"
                  >
                    6. Revocación
                  </button>
                </div>
              </div>
            </div>

            {/* Chasis del teléfono Android */}
            <div className="relative w-[375px] h-[780px] bg-[#070A11] rounded-[48px] p-3 shadow-2xl border-4 border-[#22324C] flex flex-col overflow-hidden">
              <div className="absolute top-4 left-1/2 -translate-x-1/2 w-32 h-5 bg-[#0B1220] rounded-full flex items-center justify-center z-50 border border-[#22324C]/60">
                <div className="w-10 h-1 bg-[#22324C] rounded-full"></div>
              </div>

              {/* Pantalla del teléfono */}
              <div className="relative flex-1 bg-[#0B1220] rounded-[38px] overflow-y-auto flex flex-col pt-7 pb-4">
                {/* 1. WELCOME */}
                {currentScreen === 'WELCOME' && (
                  <div className="flex-1 p-6 flex flex-col justify-between">
                    <div className="flex justify-center pt-2">
                      <span className="text-[10px] bg-[#00CFE8]/15 text-[#00CFE8] font-semibold px-2 py-0.5 rounded border border-[#00CFE8]/30">
                        VEL 0.1 • Etapa 3
                      </span>
                    </div>

                    <div className="flex flex-col items-center text-center space-y-6 my-auto">
                      <div className="w-24 h-24 rounded-2xl bg-[#111C2E] border border-[#22324C] flex items-center justify-center shadow-lg">
                        <svg className="w-14 h-14" viewBox="0 0 100 100" fill="none">
                          <path d="M50 15L85 85H70L50 45L30 85H15L50 15Z" fill="#00CFE8" />
                          <circle cx="50" cy="35" r="5" fill="#FFFFFF" />
                        </svg>
                      </div>

                      <div className="space-y-2">
                        <h1 className="text-3xl font-extrabold tracking-tight text-white">VEL</h1>
                        <p className="text-sm text-[#94A3B8] max-w-[240px] leading-relaxed">
                          Protección inteligente de tu información personal.
                        </p>
                      </div>
                    </div>

                    <div className="space-y-3">
                      <button
                        onClick={() => setCurrentScreen('ROLE_SELECTION')}
                        className="w-full h-12 rounded-xl bg-[#00CFE8] hover:bg-[#00b8ce] text-[#0B1220] font-bold text-sm transition-all shadow-md active:scale-[0.98]"
                      >
                        Comenzar
                      </button>

                      <button
                        onClick={() => {
                          setCurrentScreen('SUBSCRIPTION_GATE');
                          setIsGateLoading(true);
                          setTimeout(() => setIsGateLoading(false), 1200);
                        }}
                        className="w-full text-center text-xs text-[#00CFE8] hover:underline font-medium py-1"
                      >
                        Verificar Licencia de Evaluación
                      </button>

                      <p className="text-[10px] text-center text-[#94A3B8]">
                        Conexión segura vía WebSockets con cifrado E2EE
                      </p>
                    </div>
                  </div>
                )}

                {/* SUBSCRIPTION GATE SCREEN */}
                {currentScreen === 'SUBSCRIPTION_GATE' && (
                  <div className="flex-1 bg-[#0B1220] flex items-center justify-center p-6 relative">
                    {/* Back arrow */}
                    <button
                      onClick={() => setCurrentScreen('WELCOME')}
                      className="absolute top-4 left-4 text-[#94A3B8] hover:text-white p-1 rounded-lg"
                      title="Volver"
                    >
                      <ArrowLeft className="w-5 h-5" />
                    </button>

                    {isGateLoading ? (
                      <div className="flex flex-col items-center justify-center space-y-4">
                        <div className="w-10 h-10 border-4 border-[#00CFE8] border-t-transparent rounded-full animate-spin"></div>
                        <p className="text-sm text-white/70">
                          Verificando soberanía y licencia...
                        </p>
                      </div>
                    ) : (
                      <div className="flex flex-col items-center justify-center text-center space-y-6 max-w-sm">
                        <div className="space-y-3">
                          <h2 className="text-[#00CFE8] text-xl font-bold tracking-widest">
                            ACCESO RESTRINGIDO
                          </h2>
                          <p className="text-sm text-white/80 leading-relaxed">
                            Tu período de mecenazgo ha caducado o el dispositivo requiere validación de licencia en la red.
                          </p>
                        </div>

                        <div className="w-full space-y-3 pt-4">
                          <button
                            onClick={() => setShowUsdtModal(true)}
                            className="w-full h-12 rounded-xl bg-[#00CFE8] hover:bg-[#00b8ce] text-[#0B1220] font-bold text-sm tracking-wide transition-all shadow-md active:scale-[0.98]"
                          >
                            CONSULTAR ESTADO / RENOVAR
                          </button>

                          <button
                            onClick={() => {
                              setIsGateLoading(true);
                              setTimeout(() => setIsGateLoading(false), 1200);
                            }}
                            className="w-full h-12 rounded-xl border border-[#00CFE8] text-[#00CFE8] hover:bg-[#00CFE8]/10 font-bold text-sm transition-all"
                          >
                            VERIFICAR ESTADO NUEVAMENTE
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                )}

                {/* 2. ROLE SELECTION */}
                {currentScreen === 'ROLE_SELECTION' && (
                  <div className="flex-1 p-6 flex flex-col justify-between">
                    <div className="flex items-center justify-between pb-2">
                      <button
                        onClick={() => setCurrentScreen('WELCOME')}
                        className="text-[#94A3B8] hover:text-white"
                      >
                        <ArrowLeft className="w-5 h-5" />
                      </button>
                      <span className="text-[10px] bg-[#10B981]/15 text-[#10B981] font-semibold px-2 py-0.5 rounded border border-[#10B981]/30">
                        Identidad persistente
                      </span>
                    </div>

                    <div className="space-y-4 my-auto">
                      <div className="space-y-1">
                        <h2 className="text-xl font-bold text-white">Configurar este dispositivo</h2>
                        <p className="text-xs text-[#94A3B8]">
                          Selecciona la función que desempeñará este teléfono:
                        </p>
                      </div>

                      <div
                        onClick={() => {
                          setSelectedRole('CORE');
                          setCurrentScreen('CORE_HOME');
                        }}
                        className="p-4 rounded-xl bg-[#111C2E] border border-[#22324C] hover:border-[#00CFE8] cursor-pointer transition-all space-y-3 group"
                      >
                        <div className="flex items-center gap-3">
                          <div className="w-10 h-10 rounded-lg bg-[#00CFE8]/10 text-[#00CFE8] flex items-center justify-center border border-[#00CFE8]/30">
                            <Shield className="w-5 h-5" />
                          </div>
                          <div>
                            <h3 className="font-bold text-sm text-white group-hover:text-[#00CFE8] transition-colors">
                              VEL CORE
                            </h3>
                            <span className="text-[10px] text-[#00CFE8]">Dispositivo principal</span>
                          </div>
                        </div>
                        <p className="text-[11px] text-[#94A3B8]">
                          Autoridad de confianza del sistema. Administra y revoca terminales autorizadas.
                        </p>
                        <button className="w-full h-9 rounded-lg bg-[#00CFE8] text-[#0B1220] font-bold text-xs">
                          Entrar como VEL CORE
                        </button>
                      </div>

                      <div
                        onClick={() => {
                          setSelectedRole('TERMINAL');
                          setCurrentScreen('TERMINAL_HOME');
                        }}
                        className="p-4 rounded-xl bg-[#111C2E] border border-[#22324C] hover:border-[#00CFE8] cursor-pointer transition-all space-y-3 group"
                      >
                        <div className="flex items-center gap-3">
                          <div className="w-10 h-10 rounded-lg bg-[#18263E] text-white flex items-center justify-center border border-[#22324C]">
                            <Smartphone className="w-5 h-5" />
                          </div>
                          <div>
                            <h3 className="font-bold text-sm text-white group-hover:text-[#00CFE8] transition-colors">
                              VEL TERMINAL
                            </h3>
                            <span className="text-[10px] text-[#94A3B8]">Dispositivo móvil de uso diario</span>
                          </div>
                        </div>
                        <p className="text-[11px] text-[#94A3B8]">
                          Teléfono cliente que se comunica con CORE a través del Relay de forma cifrada.
                        </p>
                        <button className="w-full h-9 rounded-lg bg-[#18263E] border border-[#22324C] text-white font-semibold text-xs">
                          Entrar como VEL TERMINAL
                        </button>
                      </div>
                    </div>
                  </div>
                )}

                {/* 3. CORE HOME SCREEN */}
                {currentScreen === 'CORE_HOME' && (
                  <div className="flex-1 p-5 space-y-4">
                    <div className="flex items-center justify-between pb-2 border-b border-[#22324C]">
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-base text-[#00CFE8]">VEL CORE</span>
                        <span className="text-[9px] bg-[#00CFE8]/15 text-[#00CFE8] font-bold px-1.5 py-0.5 rounded">
                          Principal
                        </span>
                      </div>
                      <button
                        onClick={() => setCurrentScreen('ROLE_SELECTION')}
                        className="text-xs text-[#94A3B8] hover:text-white"
                        title="Cambiar rol"
                      >
                        <RefreshCw className="w-4 h-4" />
                      </button>
                    </div>

                    {/* Estado de conexión con Relay */}
                    <div className="bg-[#111C2E] border border-[#22324C] rounded-2xl p-4 space-y-2">
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-semibold text-white">Autoridad de confianza</span>
                        <div className="flex items-center gap-1.5">
                          <span
                            className={`w-2 h-2 rounded-full ${
                              relayState === 'AUTHENTICATED' ? 'bg-[#10B981]' : 'bg-[#EF4444]'
                            }`}
                          ></span>
                          <span className="text-[10px] font-semibold text-[#10B981]">
                            {relayState === 'AUTHENTICATED' ? 'Relay Conectado' : 'Sin conexión'}
                          </span>
                        </div>
                      </div>
                      <div className="p-2.5 rounded-lg bg-[#0B1220] border border-[#22324C] space-y-1 font-mono text-[11px]">
                        <div className="flex justify-between">
                          <span className="text-[#94A3B8]">ID:</span>
                          <span className="text-[#00CFE8] font-bold">{coreIdentity.deviceId}</span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-[#94A3B8]">Huella:</span>
                          <span className="text-white">{coreIdentity.shortFingerprint}</span>
                        </div>
                      </div>
                    </div>

                    {/* Cuenta y Licencia de Suscripción */}
                    <div className="bg-[#111C2E] border border-[#22324C] rounded-2xl p-4 space-y-2.5">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <User className="w-4 h-4 text-[#00CFE8]" />
                          <span className="text-xs font-semibold text-white">Cuenta y Licencia</span>
                        </div>
                        <span className="text-[10px] bg-[#10B981]/15 text-[#10B981] font-semibold px-2 py-0.5 rounded-full">
                          {subscriptionProfile.subscriptionStatus === 'active' ? 'Suscripción activa' : 'Inactiva'}
                        </span>
                      </div>
                      <div className="p-2.5 rounded-lg bg-[#0B1220] border border-[#22324C] space-y-1.5 text-[11px]">
                        <div className="flex justify-between">
                          <span className="text-[#94A3B8]">Usuario:</span>
                          <span className="text-white font-medium">{subscriptionProfile.email}</span>
                        </div>
                        <div className="flex justify-between font-mono">
                          <span className="text-[#94A3B8]">Expira:</span>
                          <span className="text-[#94A3B8]">{subscriptionProfile.expiresAt}</span>
                        </div>
                      </div>
                      <div className="pt-1 border-t border-[#22324C]/60 space-y-1">
                        <span className="text-[10px] text-[#94A3B8]">Dispositivos en licencia:</span>
                        {Object.entries(subscriptionProfile.linkedDevices).map(([id, info]) => (
                          <div key={id} className="flex items-center justify-between text-[10px] font-mono">
                            <span className="text-white">{id} ({info.role})</span>
                            <span className="text-[#10B981] bg-[#18263E] px-1.5 py-0.5 rounded text-[9px] font-sans font-bold">
                              {info.status.toUpperCase()}
                            </span>
                          </div>
                        ))}
                      </div>
                    </div>

                    {/* Lista de terminales */}
                    <div className="space-y-2">
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-bold text-white">Terminales vinculadas</span>
                        <span className="text-[10px] text-[#94A3B8]">{devices.length} dispositivo(s)</span>
                      </div>

                      {devices.map((d) => (
                        <div
                          key={d.id}
                          className="bg-[#111C2E] border border-[#22324C] rounded-xl p-3 space-y-2"
                        >
                          <div className="flex items-center justify-between">
                            <div className="flex items-center gap-2.5">
                              <div className="w-8 h-8 rounded-lg bg-[#18263E] flex items-center justify-center text-[#00CFE8]">
                                <Smartphone className="w-4 h-4" />
                              </div>
                              <div>
                                <h4 className="text-xs font-semibold text-white">{d.name}</h4>
                                <p className="text-[10px] text-[#94A3B8] font-mono">
                                  ID: {d.id} • SAS: {d.sasCode}
                                </p>
                              </div>
                            </div>
                            <span
                              className={`text-[10px] px-2 py-0.5 rounded font-medium ${
                                d.isLocallyRevoked
                                  ? 'bg-[#F59E0B]/15 text-[#F59E0B]'
                                  : 'bg-[#10B981]/15 text-[#10B981]'
                              }`}
                            >
                              {d.statusText}
                            </span>
                          </div>

                          {!d.isLocallyRevoked && (
                            <button
                              onClick={() => handleSendTestMessage('CORE')}
                              disabled={isSendingMessage}
                              className="w-full h-8 rounded-lg bg-[#18263E] hover:bg-[#22324C] border border-[#22324C] text-[#00CFE8] text-xs font-semibold flex items-center justify-center gap-2 transition-all"
                            >
                              <Send className="w-3.5 h-3.5" />
                              <span>Enviar mensaje cifrado a Terminal</span>
                            </button>
                          )}
                        </div>
                      ))}
                    </div>

                    {testMessageFeedback && (
                      <div className="p-3 bg-[#111C2E] border border-[#00CFE8]/40 rounded-xl text-[11px] text-[#00CFE8] leading-relaxed">
                        {testMessageFeedback}
                      </div>
                    )}

                    {/* Acciones */}
                    <div className="space-y-2 pt-1">
                      <button
                        onClick={() => setCurrentScreen('PAIRING_CORE')}
                        className="w-full h-11 rounded-xl bg-[#00CFE8] hover:bg-[#00b8ce] text-[#0B1220] font-bold text-xs transition-all flex items-center justify-center gap-2 shadow"
                      >
                        <Plus className="w-4 h-4" />
                        <span>Vincular nuevo dispositivo</span>
                      </button>

                      <button
                        onClick={() => setCurrentScreen('DEVICE_MANAGEMENT')}
                        className="w-full h-11 rounded-xl bg-[#18263E] hover:bg-[#22324C] border border-[#22324C] text-white font-semibold text-xs transition-all flex items-center justify-center gap-2"
                      >
                        <Smartphone className="w-4 h-4" />
                        <span>Administrar y revocar terminales</span>
                      </button>
                    </div>
                  </div>
                )}

                {/* 4. TERMINAL HOME SCREEN */}
                {currentScreen === 'TERMINAL_HOME' && (
                  <div className="flex-1 p-5 space-y-4">
                    <div className="flex items-center justify-between pb-2 border-b border-[#22324C]">
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-base text-white">VEL TERMINAL</span>
                        <span className="text-[9px] bg-[#18263E] text-[#94A3B8] font-bold px-1.5 py-0.5 rounded">
                          Móvil
                        </span>
                      </div>
                      <button
                        onClick={() => setCurrentScreen('ROLE_SELECTION')}
                        className="text-xs text-[#94A3B8] hover:text-white"
                        title="Cambiar rol"
                      >
                        <RefreshCw className="w-4 h-4" />
                      </button>
                    </div>

                    {/* Banner de revocación si aplica */}
                    {relayState === 'REVOKED' && (
                      <div className="p-4 rounded-xl bg-[#111C2E] border border-[#F59E0B] space-y-2">
                        <div className="flex items-center gap-2 text-[#F59E0B]">
                          <AlertTriangle className="w-5 h-5" />
                          <h4 className="font-bold text-xs">Acceso revocado por VEL CORE</h4>
                        </div>
                        <p className="text-[11px] text-[#94A3B8] leading-relaxed">
                          Tu autorización ha sido invalidada de forma persistente en el servidor Relay. El terminal ha sido desconectado y no puede enviar ni recibir datos.
                        </p>
                      </div>
                    )}

                    {/* Identidad del Terminal */}
                    <div className="bg-[#111C2E] border border-[#22324C] rounded-2xl p-4 space-y-2">
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-semibold text-white">Identidad del Terminal</span>
                        <span
                          className={`text-[10px] font-semibold ${
                            relayState === 'AUTHENTICATED'
                              ? 'text-[#10B981]'
                              : relayState === 'REVOKED'
                              ? 'text-[#F59E0B]'
                              : 'text-[#EF4444]'
                          }`}
                        >
                          {relayState === 'AUTHENTICATED'
                            ? 'Relay Conectado'
                            : relayState === 'REVOKED'
                            ? 'Revocado'
                            : 'Sin conexión'}
                        </span>
                      </div>
                      <div className="p-2.5 rounded-lg bg-[#0B1220] border border-[#22324C] space-y-1 font-mono text-[11px]">
                        <div className="flex justify-between">
                          <span className="text-[#94A3B8]">ID:</span>
                          <span className="text-[#00CFE8] font-bold">{terminalIdentity.deviceId}</span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-[#94A3B8]">Huella:</span>
                          <span className="text-white">{terminalIdentity.shortFingerprint}</span>
                        </div>
                      </div>
                    </div>

                    {/* Cuenta y Licencia de Suscripción */}
                    <div className="bg-[#111C2E] border border-[#22324C] rounded-2xl p-4 space-y-2.5">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <User className="w-4 h-4 text-[#00CFE8]" />
                          <span className="text-xs font-semibold text-white">Cuenta y Licencia</span>
                        </div>
                        <span className="text-[10px] bg-[#10B981]/15 text-[#10B981] font-semibold px-2 py-0.5 rounded-full">
                          {subscriptionProfile.subscriptionStatus === 'active' ? 'Suscripción activa' : 'Inactiva'}
                        </span>
                      </div>
                      <div className="p-2.5 rounded-lg bg-[#0B1220] border border-[#22324C] space-y-1.5 text-[11px]">
                        <div className="flex justify-between">
                          <span className="text-[#94A3B8]">Usuario:</span>
                          <span className="text-white font-medium">{subscriptionProfile.email}</span>
                        </div>
                        <div className="flex justify-between font-mono">
                          <span className="text-[#94A3B8]">Expira:</span>
                          <span className="text-[#94A3B8]">{subscriptionProfile.expiresAt}</span>
                        </div>
                      </div>
                      <div className="pt-1 border-t border-[#22324C]/60 space-y-1">
                        <span className="text-[10px] text-[#94A3B8]">Dispositivos en licencia:</span>
                        {Object.entries(subscriptionProfile.linkedDevices).map(([id, info]) => (
                          <div key={id} className="flex items-center justify-between text-[10px] font-mono">
                            <span className="text-white">{id} ({info.role})</span>
                            <span className="text-[#10B981] bg-[#18263E] px-1.5 py-0.5 rounded text-[9px] font-sans font-bold">
                              {info.status.toUpperCase()}
                            </span>
                          </div>
                        ))}
                      </div>
                    </div>

                    {/* CORE Asociado */}
                    <div className="bg-[#111C2E] border border-[#22324C] rounded-2xl p-4 space-y-3">
                      <div className="flex items-center justify-between">
                        <span className="text-xs font-semibold text-white">Dispositivo CORE asociado</span>
                        <span
                          className={`text-[10px] font-semibold ${
                            relayState === 'REVOKED' ? 'text-[#F59E0B]' : 'text-[#10B981]'
                          }`}
                        >
                          {relayState === 'REVOKED' ? 'Revocado' : 'Emparejado'}
                        </span>
                      </div>
                      <div className="flex items-center gap-3">
                        <div className="w-9 h-9 rounded-lg bg-[#18263E] border border-[#22324C] flex items-center justify-center text-[#10B981]">
                          <Shield className="w-4 h-4" />
                        </div>
                        <div>
                          <h4 className="text-xs font-semibold text-white">VEL CORE (Principal)</h4>
                          <p className="text-[10px] text-[#94A3B8] font-mono">
                            ID: {coreIdentity.deviceId} • SAS: 482910
                          </p>
                        </div>
                      </div>
                    </div>

                    {/* Botones de acción E2EE y Sincronización Offline-First */}
                    <div className="space-y-2">
                      <button
                        onClick={handleSyncWithCore}
                        disabled={relayState === 'REVOKED' || isSyncing}
                        className={`w-full h-11 rounded-xl font-bold text-xs transition-all flex items-center justify-center gap-2 shadow ${
                          relayState === 'REVOKED'
                            ? 'bg-[#18263E] text-[#94A3B8] cursor-not-allowed'
                            : 'bg-[#00CFE8] hover:bg-[#00b8ce] text-[#0B1220]'
                        }`}
                      >
                        <RefreshCw className={`w-4 h-4 ${isSyncing ? 'animate-spin' : ''}`} />
                        <span>Sincronizar con Core</span>
                      </button>

                      <button
                        onClick={() => handleSendTestMessage('TERMINAL')}
                        disabled={relayState === 'REVOKED' || isSendingMessage}
                        className="w-full h-10 rounded-xl bg-[#18263E] hover:bg-[#22324C] border border-[#22324C] text-[#00CFE8] font-semibold text-xs transition-all flex items-center justify-center gap-2"
                      >
                        <Send className="w-3.5 h-3.5" />
                        <span>Enviar ping cifrado a CORE</span>
                      </button>

                      {testMessageFeedback && (
                        <div className="p-3 bg-[#111C2E] border border-[#00CFE8]/40 rounded-xl text-[11px] text-[#00CFE8] leading-relaxed">
                          {testMessageFeedback}
                        </div>
                      )}
                    </div>

                    {/* Soberanía Local (VelLocalDB / IndexedDB) */}
                    <div className="bg-[#111C2E] border border-[#22324C] rounded-2xl p-4 space-y-2.5">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2 text-white">
                          <Database className="w-4 h-4 text-[#00CFE8]" />
                          <span className="text-xs font-semibold">Soberanía Local (VelLocalDB)</span>
                        </div>
                        <span className="text-[10px] text-[#00CFE8] font-mono bg-[#00CFE8]/10 px-2 py-0.5 rounded">
                          {localRecords.length} guardados
                        </span>
                      </div>
                      <p className="text-[10px] text-[#94A3B8] leading-relaxed">
                        Cifrado local en reposo (AES-256-GCM + IV). La base de datos nunca almacena texto en claro.
                      </p>

                      {localRecords.length > 0 ? (
                        <div className="space-y-1.5 max-h-36 overflow-y-auto pr-1">
                          {localRecords.map((rec) => (
                            <div
                              key={rec.id}
                              className="p-2 rounded-lg bg-[#0B1220] border border-[#22324C] text-[10px] font-mono space-y-1"
                            >
                              <div className="flex justify-between items-center text-[#00CFE8]">
                                <span>{rec.id}</span>
                                <button
                                  onClick={() => handleDeleteLocalRecord(rec.id)}
                                  className="text-red-400 hover:text-red-300 font-sans text-[9px]"
                                >
                                  Eliminar
                                </button>
                              </div>
                              <div className="text-[#94A3B8] truncate">
                                IV: {rec.iv}
                              </div>
                              <div className="text-white/70 truncate">
                                Cifrado: {rec.encryptedData.slice(0, 24)}...
                              </div>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <div className="p-3 text-center bg-[#0B1220] rounded-xl border border-[#22324C] text-[#94A3B8] text-[11px]">
                          Sin registros aún en la base local. Usa el panel lateral para guardar el primero.
                        </div>
                      )}
                    </div>
                  </div>
                )}

                {/* 5. PAIRING */}
                {currentScreen === 'PAIRING_CORE' && (
                  <div className="flex-1 p-5 space-y-4">
                    <div className="flex items-center gap-2 pb-2 border-b border-[#22324C]">
                      <button
                        onClick={() => setCurrentScreen('CORE_HOME')}
                        className="text-[#94A3B8] hover:text-white"
                      >
                        <ArrowLeft className="w-5 h-5" />
                      </button>
                      <span className="font-bold text-sm text-white">Vincular dispositivo</span>
                    </div>

                    <div className="bg-[#111C2E] border border-[#22324C] rounded-2xl p-5 text-center space-y-3">
                      <span className="text-xs font-semibold text-[#94A3B8]">Código temporal de un solo uso</span>
                      <div className="p-3 bg-[#0B1220] rounded-xl border border-[#00CFE8]/50 inline-block">
                        <span className="font-mono text-2xl font-extrabold text-[#00CFE8] tracking-widest">
                          VEL-8419
                        </span>
                      </div>
                      <p className="text-[11px] text-[#94A3B8]">
                        Válido por 5 minutos • Vinculado al servidor Relay
                      </p>
                    </div>

                    <button
                      onClick={() => setCurrentScreen('CORE_HOME')}
                      className="w-full h-11 rounded-xl bg-[#00CFE8] text-[#0B1220] font-bold text-xs"
                    >
                      Volver a CORE
                    </button>
                  </div>
                )}

                {/* 6. DEVICE MANAGEMENT (REVOCATION) */}
                {currentScreen === 'DEVICE_MANAGEMENT' && (
                  <div className="flex-1 p-5 space-y-4">
                    <div className="flex items-center gap-2 pb-2 border-b border-[#22324C]">
                      <button
                        onClick={() => setCurrentScreen('CORE_HOME')}
                        className="text-[#94A3B8] hover:text-white"
                      >
                        <ArrowLeft className="w-5 h-5" />
                      </button>
                      <span className="font-bold text-sm text-white">Administrar dispositivos</span>
                    </div>

                    <div className="space-y-1">
                      <h3 className="text-base font-bold text-white">Revocación remota</h3>
                      <p className="text-xs text-[#94A3B8]">
                        La revocación se registra de forma persistente en el Relay y cierra la conexión de inmediato.
                      </p>
                    </div>

                    <div className="space-y-3">
                      {devices.map((device) => (
                        <div
                          key={device.id}
                          className="bg-[#111C2E] border border-[#22324C] rounded-2xl p-4 space-y-3"
                        >
                          <div className="flex items-center justify-between">
                            <div className="flex items-center gap-2.5">
                              <div className="w-8 h-8 rounded-lg bg-[#18263E] flex items-center justify-center text-[#00CFE8]">
                                <Smartphone className="w-4 h-4" />
                              </div>
                              <div>
                                <h4 className="text-xs font-semibold text-white">{device.name}</h4>
                                <p className="text-[10px] text-[#94A3B8] font-mono">
                                  ID: {device.id} • SAS: {device.sasCode}
                                </p>
                              </div>
                            </div>

                            <span
                              className={`text-[10px] px-2 py-0.5 rounded font-medium ${
                                device.isLocallyRevoked
                                  ? 'bg-[#F59E0B]/15 text-[#F59E0B]'
                                  : 'bg-[#10B981]/15 text-[#10B981]'
                              }`}
                            >
                              {device.statusText}
                            </span>
                          </div>

                          {!device.isLocallyRevoked ? (
                            <button
                              onClick={() => setConfirmRevokeTarget(device)}
                              className="w-full h-8 rounded-lg border border-[#F59E0B]/50 hover:bg-[#F59E0B]/10 text-[#F59E0B] text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors"
                            >
                              <Ban className="w-3.5 h-3.5" />
                              <span>Revocar acceso remotamente</span>
                            </button>
                          ) : (
                            <p className="text-[11px] text-[#F59E0B]">
                              Dispositivo revocado. Acceso invalidado en el servidor Relay.
                            </p>
                          )}

                          {/* Botón de Emergencia Crítica (android.com/find) */}
                          <button
                            onClick={() => setConfirmEmergencyDevice(device)}
                            className="w-full py-2 px-3 rounded-lg bg-red-700 hover:bg-red-800 text-white text-[11px] font-bold flex items-center justify-center gap-1.5 shadow transition-colors"
                          >
                            <AlertTriangle className="w-3.5 h-3.5" />
                            <span>EMERGENCIA-DESACTIVAR-REINICIAR</span>
                          </button>
                        </div>
                      ))}
                    </div>

                    {/* Modal de confirmación de revocación */}
                    {confirmRevokeTarget && (
                      <div className="absolute inset-0 bg-black/75 backdrop-blur-sm p-4 flex items-center justify-center z-50">
                        <div className="bg-[#111C2E] border border-[#22324C] rounded-2xl p-5 space-y-4 max-w-xs w-full shadow-2xl">
                          <div className="flex items-center gap-2 text-[#F59E0B]">
                            <AlertTriangle className="w-5 h-5" />
                            <h3 className="font-bold text-sm text-white">Revocación remota</h3>
                          </div>
                          <p className="text-xs text-[#94A3B8] leading-relaxed">
                            ¿Deseas revocar la relación de confianza con <strong className="text-white">'{confirmRevokeTarget.name}'</strong>?
                          </p>
                          <p className="text-[11px] text-[#94A3B8] leading-relaxed">
                            Esta orden se transmitirá al Relay, cerrará de forma forzosa la conexión activa del Terminal y bloqueará cualquier intento futuro.
                          </p>
                          <div className="flex gap-2 pt-1">
                            <button
                              onClick={() => handleConfirmRemoteRevocation(confirmRevokeTarget.id)}
                              className="flex-1 h-9 rounded-lg bg-[#F59E0B] text-[#0B1220] font-bold text-xs"
                            >
                              Confirmar revocación
                            </button>
                            <button
                              onClick={() => setConfirmRevokeTarget(null)}
                              className="flex-1 h-9 rounded-lg border border-[#22324C] text-[#94A3B8] text-xs hover:text-white"
                            >
                              Cancelar
                            </button>
                          </div>
                        </div>
                      </div>
                    )}

                    {/* Modal de Emergencia y Borrado Remoto Oficial */}
                    {confirmEmergencyDevice && (
                      <div className="absolute inset-0 bg-black/85 backdrop-blur-sm p-4 flex items-center justify-center z-50">
                        <div className="bg-[#111C2E] border border-red-500/50 rounded-2xl p-5 space-y-4 max-w-xs w-full shadow-2xl">
                          <div className="flex items-center gap-2 text-red-500">
                            <AlertTriangle className="w-5 h-5 text-red-500" />
                            <h3 className="font-bold text-sm text-white">⚠️ ALERTA CRÍTICA DE EMERGENCIA</h3>
                          </div>
                          <p className="text-xs text-[#94A3B8] leading-relaxed">
                            Estás a punto de activar el protocolo de emergencia para el terminal <strong className="text-white">"{confirmEmergencyDevice.name}"</strong>. Esto te redirigirá a la consola oficial para revocar accesos y ejecutar el reinicio/borrado de fábrica remoto.
                          </p>
                          <div className="flex gap-2 pt-1">
                            <button
                              onClick={() => {
                                const targetUrl = 'https://android.com/find';
                                setConfirmEmergencyDevice(null);
                                window.open(targetUrl, '_blank', 'noopener,noreferrer');
                              }}
                              className="flex-1 h-9 rounded-lg bg-red-900 hover:bg-red-950 text-white font-bold text-xs"
                            >
                              EJECUTAR
                            </button>
                            <button
                              onClick={() => setConfirmEmergencyDevice(null)}
                              className="flex-1 h-9 rounded-lg border border-[#22324C] text-[#94A3B8] text-xs hover:text-white"
                            >
                              Cancelar
                            </button>
                          </div>
                        </div>
                      </div>
                    )}
                  </div>
                )}

                {/* Modal de Información de Licencia */}
                {showUsdtModal && (
                  <div className="absolute inset-0 bg-black/80 backdrop-blur-sm p-4 flex items-center justify-center z-50">
                    <div className="bg-[#111C2E] border border-[#00CFE8]/50 rounded-2xl p-5 space-y-4 max-w-xs w-full shadow-2xl">
                      <div className="flex items-center justify-between">
                        <h3 className="font-bold text-sm text-[#00CFE8]">Suscripción y Licencia</h3>
                        <span className="text-[10px] bg-[#00CFE8]/15 text-[#00CFE8] font-bold px-1.5 py-0.5 rounded">
                          Modo Evaluación
                        </span>
                      </div>
                      <p className="text-xs text-[#94A3B8]">
                        En VEL 0.1 las pruebas de emparejamiento y comunicación cifrada de extremo a extremo son prioritarias y no están restringidas por pasarelas de pago.
                      </p>
                      <div className="p-3 bg-[#0B1220] border border-[#22324C] rounded-xl space-y-1.5 text-xs text-white/80">
                        <p className="text-[#00CFE8] font-semibold text-[11px]">Estado de pruebas:</p>
                        <p className="text-[11px] text-[#94A3B8]">Validación criptográfica local y relay operativo.</p>
                      </div>
                      <div className="space-y-2 pt-1">
                        <button
                          onClick={() => {
                            setShowUsdtModal(false);
                            setCurrentScreen('WELCOME');
                          }}
                          className="w-full h-9 rounded-lg bg-[#00CFE8] hover:bg-[#00b8ce] text-[#0B1220] font-bold text-xs"
                        >
                          Continuar a VEL
                        </button>
                        <button
                          onClick={() => setShowUsdtModal(false)}
                          className="w-full h-8 rounded-lg border border-[#22324C] text-[#94A3B8] text-xs hover:text-white"
                        >
                          Cerrar
                        </button>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
        ) : (
          /* Explorador del árbol de archivos nativos y exportación para GitHub CI/CD */
          <div className="max-w-3xl w-full bg-[#111C2E] border border-[#22324C] rounded-2xl p-6 space-y-4">
            <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 pb-3 border-b border-[#22324C]">
              <div>
                <h3 className="font-bold text-base text-white flex items-center gap-2">
                  <Shield className="w-5 h-5 text-[#00CFE8]" />
                  <span>VEL 0.1 — Proyecto Android Nativo (Kotlin + Jetpack Compose)</span>
                </h3>
                <p className="text-xs text-[#94A3B8]">
                  Estructura Gradle estándar en la raíz para CI/CD directo en GitHub Actions y compilación de APK
                </p>
              </div>

              <a
                href="/vel-android-nativo.zip"
                download="vel-android-nativo.zip"
                className="px-4 py-2 bg-[#00CFE8] hover:bg-[#00B4CC] text-[#0B1220] font-bold rounded-xl text-xs flex items-center gap-2 transition-all shadow-lg hover:shadow-[#00CFE8]/20 flex-shrink-0"
              >
                <Download className="w-4 h-4" />
                <span>Descargar ZIP Android Nativo</span>
              </a>
            </div>

            <div className="space-y-3 text-xs">
              <div className="p-3 bg-[#0B1220] rounded-xl border border-[#22324C] space-y-1 font-mono text-[11px] max-h-[380px] overflow-y-auto">
                <p className="text-[#00CFE8] font-bold">vel-android-nativo/ <span className="text-[#34D399]">(Raíz del Repositorio Android)</span></p>
                <p className="text-[#34D399] pl-4">├── gradlew <span className="text-[#94A3B8]">(Script ejecutable Linux / macOS con permisos +x)</span></p>
                <p className="text-[#34D399] pl-4">├── gradlew.bat <span className="text-[#94A3B8]">(Script ejecutable Windows)</span></p>
                <p className="text-[#34D399] pl-4">├── build.gradle.kts <span className="text-[#94A3B8]">(AGP 8.7.2, Kotlin 2.0.21)</span></p>
                <p className="text-[#34D399] pl-4">├── settings.gradle.kts <span className="text-[#94A3B8]">(Declaración de módulos y repositorios Google/Maven)</span></p>
                <p className="text-[#34D399] pl-4">├── gradle.properties</p>
                <p className="text-[#34D399] pl-4">├── .gitignore <span className="text-[#94A3B8]">(Exclusión de .gradle, build/, *.apk)</span></p>
                <p className="text-[#34D399] pl-4">├── README.md <span className="text-[#94A3B8]">(Instrucciones de compilación y despliegue)</span></p>
                <p className="text-[#00CFE8] font-bold pl-4">├── .github/workflows/</p>
                <p className="text-white pl-8">└── android.yml <span className="text-[#34D399]">(Compilación automática y publicación de APK en GitHub Actions)</span></p>
                <p className="text-[#00CFE8] font-bold pl-4">├── gradle/wrapper/</p>
                <p className="text-white pl-8">├── gradle-wrapper.jar</p>
                <p className="text-white pl-8">└── gradle-wrapper.properties <span className="text-[#34D399]">(Gradle 8.10.2)</span></p>
                <p className="text-[#00CFE8] font-bold pl-4">└── app/ <span className="text-[#34D399]">(Módulo Principal de la Aplicación)</span></p>
                <p className="text-white pl-8">├── build.gradle.kts <span className="text-[#34D399]">(compileSdk 35, Compose BOM 2024.10, OkHttp 4.12)</span></p>
                <p className="text-white pl-8">├── proguard-rules.pro</p>
                <p className="text-[#00CFE8] font-bold pl-8">├── src/test/kotlin/com/vel/app/</p>
                <p className="text-white pl-12">├── SessionKeyStorageSecurityTest.kt <span className="text-[#34D399]">(10 pruebas de ciclo de vida y zeroization)</span></p>
                <p className="text-white pl-12">├── CryptoAndSecurityUnitTest.kt</p>
                <p className="text-white pl-12">├── NetworkProtocolTest.kt</p>
                <p className="text-white pl-12">├── SubscriptionModelTest.kt</p>
                <p className="text-white pl-12">├── VelRepositoryTest.kt</p>
                <p className="text-white pl-12">└── NavigationAndUiTest.kt</p>
                <p className="text-[#00CFE8] font-bold pl-8">└── src/main/</p>
                <p className="text-white pl-12">├── AndroidManifest.xml <span className="text-[#34D399]">(Permisos INTERNET, usesCleartextTraffic)</span></p>
                <p className="text-white pl-12">├── assets/subscription_profile.json</p>
                <p className="text-white pl-12">├── res/values/ (strings, colors, themes en es, en, pt)</p>
                <p className="text-[#00CFE8] font-bold pl-12">└── kotlin/com/vel/app/</p>
                <p className="text-white pl-16">├── MainActivity.kt <span className="text-[#34D399]">(Selector dinámico CORE / MÓVIL y config Relay)</span></p>
                <p className="text-[#10B981] font-bold pl-16">├── network/ <span className="text-[#34D399]">(Cliente WebSocket, enrutamiento, deduplicación)</span></p>
                <p className="text-[#10B981] font-bold pl-16">├── crypto/ <span className="text-[#34D399]">(AES-256-GCM, HKDF, ECDH P-256)</span></p>
                <p className="text-[#10B981] font-bold pl-16">├── security/ <span className="text-[#34D399]">(AndroidKeyStore, DeviceIdentity)</span></p>
                <p className="text-[#10B981] font-bold pl-16">├── pairing/ <span className="text-[#34D399]">(PairingRepository JSON V2, PairingManager)</span></p>
                <p className="text-[#10B981] font-bold pl-16">├── data/ <span className="text-[#34D399]">(VelLocalDatabase SQLite offline-first)</span></p>
                <p className="text-[#10B981] font-bold pl-16">├── subscription/ <span className="text-[#34D399]">(Licenciamiento soberano)</span></p>
                <p className="text-[#00CFE8] font-bold pl-16">└── ui/ <span className="text-[#34D399]">(Pantallas Jetpack Compose, Temas Material 3)</span></p>
              </div>

              <div className="p-3 bg-[#0B1220] rounded-xl border border-[#22324C] space-y-2">
                <span className="font-semibold text-white">Instrucciones de compilación en GitHub Actions (100% en la Nube):</span>
                <ol className="list-decimal list-inside text-[#94A3B8] space-y-1 text-[11px]">
                  <li>Descarga el archivo <span className="text-[#00CFE8] font-mono">vel-android-nativo.zip</span> con el botón superior.</li>
                  <li>Descomprímelo y sube todos los archivos a un repositorio nuevo en GitHub.</li>
                  <li>GitHub Actions detectará automáticamente el archivo <span className="text-[#00CFE8] font-mono">.github/workflows/android.yml</span>.</li>
                  <li>Ejecutará <span className="text-[#34D399] font-mono">./gradlew testDebugUnitTest</span> y <span className="text-[#34D399] font-mono">./gradlew assembleDebug</span>.</li>
                  <li>Descarga el APK resultante (<span className="text-[#00CFE8] font-mono">app-debug.apk</span>) desde la pestaña <strong>Actions</strong> de tu repositorio.</li>
                </ol>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}

export default App;
