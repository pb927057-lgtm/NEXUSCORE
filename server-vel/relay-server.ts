import http from 'http';
import fs from 'fs';
import path from 'path';
import { WebSocketServer, WebSocket } from 'ws';

export interface AuthorizationRecord {
  terminalId: string;
  coreId: string;
  pairedAt: number;
  fingerprint: string;
}

export interface RevocationRecord {
  terminalId: string;
  revokedByCoreId: string;
  revokedAt: number;
  reason: string;
}

export interface ClientConnection {
  ws: WebSocket;
  deviceId: string;
  role: 'CORE' | 'TERMINAL';
  fingerprint: string;
  authenticated: boolean;
}

export class VelRelayServer {
  private port: number;
  private dataDir: string;
  private authFile: string;
  private revFile: string;
  private server: http.Server | null = null;
  private wss: WebSocketServer | null = null;
  private clients = new Map<string, ClientConnection>();
  private authorizations = new Map<string, AuthorizationRecord>();
  private revocations = new Map<string, RevocationRecord>();
  private pairingOffers = new Map<
    string,
    {
      code: string;
      initiatorDeviceId: string;
      initiatorRole: 'CORE' | 'TERMINAL';
      initiatorFingerprint: string;
      initiatorPublicKeyBase64: string;
      expiresAt: number;
      ws: WebSocket;
    }
  >();

  constructor(port = 8080, storageDir?: string) {
    this.port = port;
    this.dataDir = storageDir || path.join(process.cwd(), 'server-vel', 'data');
    this.authFile = path.join(this.dataDir, 'authorizations.json');
    this.revFile = path.join(this.dataDir, 'revocations.json');
    this.ensureStorageDir();
    this.loadPersistence();
  }

  private ensureStorageDir() {
    if (!fs.existsSync(this.dataDir)) {
      fs.mkdirSync(this.dataDir, { recursive: true });
    }
  }

  public loadPersistence() {
    try {
      if (fs.existsSync(this.authFile)) {
        const raw = fs.readFileSync(this.authFile, 'utf8');
        const list: AuthorizationRecord[] = JSON.parse(raw);
        this.authorizations.clear();
        for (const item of list) {
          this.authorizations.set(item.terminalId, item);
        }
      }
    } catch (e) {
      console.error('[Relay] Error al cargar autorizaciones:', e);
    }

    try {
      if (fs.existsSync(this.revFile)) {
        const raw = fs.readFileSync(this.revFile, 'utf8');
        const list: RevocationRecord[] = JSON.parse(raw);
        this.revocations.clear();
        for (const item of list) {
          this.revocations.set(item.terminalId, item);
        }
      }
    } catch (e) {
      console.error('[Relay] Error al cargar revocaciones:', e);
    }
  }

  public savePersistence() {
    try {
      this.ensureStorageDir();
      const authList = Array.from(this.authorizations.values());
      fs.writeFileSync(this.authFile, JSON.stringify(authList, null, 2), 'utf8');

      const revList = Array.from(this.revocations.values());
      fs.writeFileSync(this.revFile, JSON.stringify(revList, null, 2), 'utf8');
    } catch (e) {
      console.error('[Relay] Error al guardar persistencia:', e);
    }
  }

  public start(): Promise<number> {
    return new Promise((resolve) => {
      this.server = http.createServer((req, res) => {
        if (req.url === '/health') {
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(
            JSON.stringify({
              status: 'UP',
              service: 'VEL-Relay',
              activeConnections: this.clients.size,
              authorizationsCount: this.authorizations.size,
              revocationsCount: this.revocations.size
            })
          );
          return;
        }
        res.writeHead(404);
        res.end();
      });

      this.wss = new WebSocketServer({ server: this.server });

      this.wss.on('connection', (ws: WebSocket, req: http.IncomingMessage) => {
        let currentDeviceId: string | null = null;

        // Soporte para endpoint /ws/{deviceId}
        const urlMatch = req.url?.match(/^\/ws\/([^?\/]+)/);
        if (urlMatch) {
          const pathDeviceId = decodeURIComponent(urlMatch[1]);
          if (this.revocations.has(pathDeviceId)) {
            ws.send(JSON.stringify({ type: 'AUTH_ERROR', error: 'DEVICE_REVOKED', message: 'Dispositivo revocado por CORE' }));
            ws.close(4003, 'Device is revoked');
            return;
          }
          currentDeviceId = pathDeviceId;
          this.clients.set(pathDeviceId, {
            ws,
            deviceId: pathDeviceId,
            role: pathDeviceId.includes('CORE') ? 'CORE' : 'TERMINAL',
            fingerprint: '',
            authenticated: true
          });
          ws.send(JSON.stringify({ type: 'AUTH_SUCCESS', deviceId: pathDeviceId, timestamp: Date.now() }));
        }

        ws.on('message', (messageRaw: string | Buffer) => {
          try {
            const data = JSON.parse(messageRaw.toString());
            this.handleMessage(ws, data, (devId) => {
              currentDeviceId = devId;
            });
          } catch (err: any) {
            ws.send(JSON.stringify({ type: 'ERROR', error: 'INVALID_JSON', message: err.message }));
          }
        });

        ws.on('close', () => {
          if (currentDeviceId && this.clients.has(currentDeviceId)) {
            const client = this.clients.get(currentDeviceId);
            if (client && client.ws === ws) {
              this.clients.delete(currentDeviceId);
            }
          }
        });

        ws.on('error', () => {
          if (currentDeviceId) {
            this.clients.delete(currentDeviceId);
          }
        });
      });

      this.server.listen(this.port, () => {
        const address = this.server?.address();
        const actualPort = typeof address === 'object' && address ? address.port : this.port;
        this.port = actualPort;
        resolve(actualPort);
      });
    });
  }

  public stop(): Promise<void> {
    return new Promise((resolve) => {
      for (const client of this.clients.values()) {
        try {
          client.ws.terminate();
        } catch {}
      }
      this.clients.clear();

      if (this.wss) {
        this.wss.close(() => {
          if (this.server) {
            this.server.close(() => resolve());
          } else {
            resolve();
          }
        });
      } else {
        resolve();
      }
    });
  }

  private handleMessage(
    ws: WebSocket,
    msg: any,
    registerDeviceId: (id: string) => void
  ) {
    switch (msg.type) {
      case 'AUTH': {
        const { deviceId, role, fingerprint, pairedCoreId } = msg;
        if (!deviceId || !role) {
          ws.send(JSON.stringify({ type: 'AUTH_ERROR', error: 'MISSING_FIELDS' }));
          return;
        }

        // 1. Comprobar si el dispositivo está revocado
        if (this.revocations.has(deviceId)) {
          const rev = this.revocations.get(deviceId)!;
          ws.send(
            JSON.stringify({
              type: 'AUTH_ERROR',
              error: 'DEVICE_REVOKED',
              message: `Dispositivo revocado por ${rev.revokedByCoreId}. Razón: ${rev.reason}`
            })
          );
          // Cerrar inmediatamente con código 4003 (Forbidden / Revocado)
          ws.close(4003, 'Device is revoked');
          return;
        }

        // 2. Si es Terminal, verificar si está registrado con su CORE correspondiente
        if (role === 'TERMINAL' && pairedCoreId) {
          const auth = this.authorizations.get(deviceId);
          if (!auth || auth.coreId !== pairedCoreId) {
            ws.send(
              JSON.stringify({
                type: 'AUTH_ERROR',
                error: 'UNAUTHORIZED_CORE',
                message: 'El terminal no está autorizado por el CORE indicado.'
              })
            );
            ws.close(4003, 'Unauthorized CORE');
            return;
          }
        }

        // Registrar cliente activo
        this.clients.set(deviceId, {
          ws,
          deviceId,
          role,
          fingerprint: fingerprint || '',
          authenticated: true
        });
        registerDeviceId(deviceId);

        ws.send(
          JSON.stringify({
            type: 'AUTH_SUCCESS',
            deviceId,
            timestamp: Date.now()
          })
        );
        break;
      }

      case 'PAIRING_REGISTER': {
        // CORE registra una nueva terminal autorizada
        const { coreId, terminalId, terminalFingerprint } = msg;
        const sender = this.clients.get(coreId);
        if (!sender || sender.ws !== ws || sender.role !== 'CORE') {
          ws.send(JSON.stringify({ type: 'ERROR', error: 'UNAUTHORIZED_ACTION', message: 'Solo CORE puede registrar emparejamientos.' }));
          return;
        }

        // Si estaba revocado previamente, eliminar de revocaciones
        this.revocacionesDelete(terminalId);

        // Guardar autorización persistente
        this.authorizations.set(terminalId, {
          terminalId,
          coreId,
          pairedAt: Date.now(),
          fingerprint: terminalFingerprint || ''
        });
        this.savePersistence();

        ws.send(
          JSON.stringify({
            type: 'PAIRING_REGISTERED',
            terminalId,
            timestamp: Date.now()
          })
        );
        break;
      }

      case 'PAIRING_OFFER': {
        const { code, initiatorDeviceId, initiatorRole, initiatorFingerprint, initiatorPublicKeyBase64, expiresAt } = msg;
        if (!code || !initiatorDeviceId || !initiatorPublicKeyBase64) {
          ws.send(JSON.stringify({ type: 'PAIRING_ERROR', error: 'MISSING_FIELDS', message: 'Datos incompletos para oferta de emparejamiento.' }));
          return;
        }
        const cleanCode = String(code).trim().toUpperCase();
        this.pairingOffers.set(cleanCode, {
          code: cleanCode,
          initiatorDeviceId,
          initiatorRole: (initiatorRole || 'CORE') as 'CORE' | 'TERMINAL',
          initiatorFingerprint: initiatorFingerprint || '',
          initiatorPublicKeyBase64,
          expiresAt: expiresAt || (Date.now() + 5 * 60 * 1000),
          ws
        });

        ws.send(
          JSON.stringify({
            type: 'PAIRING_OFFER_REGISTERED',
            code: cleanCode,
            expiresAt: expiresAt || (Date.now() + 5 * 60 * 1000),
            timestamp: Date.now()
          })
        );
        break;
      }

      case 'PAIRING_CLAIM': {
        const { code, claimerDeviceId, claimerRole, claimerFingerprint, claimerPublicKeyBase64 } = msg;
        if (!code || !claimerDeviceId || !claimerPublicKeyBase64) {
          ws.send(JSON.stringify({ type: 'PAIRING_ERROR', error: 'MISSING_FIELDS', message: 'Datos incompletos para reclamar código.' }));
          return;
        }

        const cleanCode = String(code).trim().toUpperCase();
        const offer = this.pairingOffers.get(cleanCode);

        if (!offer) {
          ws.send(
            JSON.stringify({
              type: 'PAIRING_ERROR',
              error: 'OFFER_NOT_FOUND',
              message: `El código '${cleanCode}' no existe o no corresponde a una sesión activa.`
            })
          );
          return;
        }

        if (Date.now() > offer.expiresAt) {
          this.pairingOffers.delete(cleanCode);
          ws.send(
            JSON.stringify({
              type: 'PAIRING_ERROR',
              error: 'OFFER_EXPIRED',
              message: 'El código de emparejamiento ha expirado (límite de 5 minutos excedido).'
            })
          );
          return;
        }

        // 1. Responder al reclamante (Terminal) con la clave pública y metadatos de CORE
        ws.send(
          JSON.stringify({
            type: 'PAIRING_OFFER_FOUND',
            code: cleanCode,
            peerDeviceId: offer.initiatorDeviceId,
            peerRole: offer.initiatorRole,
            peerFingerprint: offer.initiatorFingerprint,
            peerPublicKeyBase64: offer.initiatorPublicKeyBase64,
            timestamp: Date.now()
          })
        );

        // 2. Notificar al iniciador (CORE) con la clave pública y metadatos del reclamante
        if (offer.ws.readyState === WebSocket.OPEN) {
          offer.ws.send(
            JSON.stringify({
              type: 'PAIRING_CLAIM_RECEIVED',
              code: cleanCode,
              peerDeviceId: claimerDeviceId,
              peerRole: claimerRole || 'TERMINAL',
              peerFingerprint: claimerFingerprint || '',
              peerPublicKeyBase64: claimerPublicKeyBase64,
              timestamp: Date.now()
            })
          );
        }

        // 3. Registrar automáticamente la relación autorizada en el relay
        const coreId = offer.initiatorRole === 'CORE' ? offer.initiatorDeviceId : claimerDeviceId;
        const terminalId = offer.initiatorRole === 'CORE' ? claimerDeviceId : offer.initiatorDeviceId;
        const termFingerprint = offer.initiatorRole === 'CORE' ? (claimerFingerprint || '') : offer.initiatorFingerprint;

        this.revocacionesDelete(terminalId);
        this.authorizations.set(terminalId, {
          terminalId,
          coreId,
          pairedAt: Date.now(),
          fingerprint: termFingerprint
        });
        this.savePersistence();

        // 4. Consumir la oferta (protección contra reutilización)
        this.pairingOffers.delete(cleanCode);
        break;
      }

      case 'ENCRYPTED_MESSAGE': {
        const senderId = msg.senderId;
        const recipientId = msg.targetId || msg.recipientId;
        const ciphertext = msg.data || msg.ciphertext;
        const { operationId, iv, sessionId, timestamp } = msg;
        const sender = this.clients.get(senderId);

        if (!sender || sender.ws !== ws || !sender.authenticated) {
          ws.send(JSON.stringify({ type: 'ERROR', error: 'UNAUTHENTICATED' }));
          return;
        }

        // Verificar que el remitente o destinatario no estén revocados
        if (this.revocations.has(senderId) || this.revocations.has(recipientId)) {
          ws.send(JSON.stringify({ type: 'ERROR', error: 'DEVICE_REVOKED', operationId }));
          return;
        }

        // El relay NO descifra el mensaje. Solo valida autorización y enruta al destinatario.
        const recipient = this.clients.get(recipientId);
        if (!recipient || recipient.ws.readyState !== WebSocket.OPEN) {
          // Minimización de datos: NO guardar en cola persistente
          ws.send(
            JSON.stringify({
              type: 'DELIVERY_FAILED',
              operationId,
              recipientId,
              reason: 'RECIPIENT_OFFLINE',
              message: 'El destinatario no está conectado actualmente. Mensaje descartado.'
            })
          );
          return;
        }

        // Reenviar paquete cifrado al destinatario autorizado
        recipient.ws.send(
          JSON.stringify({
            type: 'ENCRYPTED_MESSAGE',
            senderId,
            recipientId,
            sessionId,
            operationId,
            iv,
            ciphertext,
            timestamp: timestamp || Date.now()
          })
        );
        break;
      }

      case 'CONFIRMATION': {
        const { senderId, recipientId, operationId, status, timestamp } = msg;
        const sender = this.clients.get(senderId);
        if (!sender || sender.ws !== ws) {
          ws.send(JSON.stringify({ type: 'ERROR', error: 'UNAUTHENTICATED' }));
          return;
        }

        const recipient = this.clients.get(recipientId);
        if (recipient && recipient.ws.readyState === WebSocket.OPEN) {
          recipient.ws.send(
            JSON.stringify({
              type: 'CONFIRMATION',
              senderId,
              recipientId,
              operationId,
              status: status || 'CONFIRMED',
              timestamp: timestamp || Date.now()
            })
          );
        }
        break;
      }

      case 'REVOKE_DEVICE': {
        const { coreId, targetTerminalId, reason } = msg;
        const sender = this.clients.get(coreId);

        // Validar que el emisor sea un CORE autenticado
        if (!sender || sender.ws !== ws || sender.role !== 'CORE') {
          ws.send(JSON.stringify({ type: 'ERROR', error: 'UNAUTHORIZED_ACTION', message: 'Solo CORE puede solicitar revocaciones.' }));
          return;
        }

        // Registrar revocación persistente
        this.revocations.set(targetTerminalId, {
          terminalId: targetTerminalId,
          revokedByCoreId: coreId,
          revokedAt: Date.now(),
          reason: reason || 'Revocado por CORE'
        });
        this.authorizations.delete(targetTerminalId);
        this.savePersistence();

        // Si la terminal objetivo tiene un WebSocket abierto, cerrarlo inmediatamente
        const activeTerminal = this.clients.get(targetTerminalId);
        if (activeTerminal) {
          try {
            activeTerminal.ws.send(
              JSON.stringify({
                type: 'REVOCATION_NOTICE',
                message: 'Tu autorización ha sido revocada por VEL CORE.',
                timestamp: Date.now()
              })
            );
            activeTerminal.ws.close(4003, 'Revoked by CORE');
          } catch {}
          this.clients.delete(targetTerminalId);
        }

        // Responder confirmación formal a CORE
        ws.send(
          JSON.stringify({
            type: 'REVOCATION_CONFIRMED',
            targetTerminalId,
            timestamp: Date.now()
          })
        );
        break;
      }

      default:
        ws.send(JSON.stringify({ type: 'ERROR', error: 'UNKNOWN_MESSAGE_TYPE' }));
        break;
    }
  }

  private revocacionesDelete(id: string) {
    this.revocations.delete(id);
    this.savePersistence();
  }

  public getActiveClientCount(): number {
    return this.clients.size;
  }

  public isRevoked(deviceId: string): boolean {
    return this.revocations.has(deviceId);
  }
}

// Punto de entrada si se ejecuta directamente con tsx
if (import.meta.url === `file://${process.argv[1]}`) {
  const port = parseInt(process.env.RELAY_PORT || '8080', 10);
  const server = new VelRelayServer(port);
  server.start().then((actualPort) => {
    console.log(`[VEL Relay Server] Ejecutándose en puerto ${actualPort}`);
  });
}
