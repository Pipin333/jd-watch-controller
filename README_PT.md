# 🕺 JD Watch Controller (Wear OS + Android) — v0.1.0

[English](README.md) • [Español](README_ES.md) • **[Português](README_PT.md)**

> **Controle de pontuação de movimento de código aberto para Just Dance em smartwatches Wear OS (Samsung Galaxy Watch 4, 5, 6, 7 / Pixel Watch) e smartphones Android.**  
> Compatível com Just Dance 2017 PC, Just Dance 2018–2022 e servidores/mods comunitários (*Legacy PC / JDLO*).

---

## 🌟 Novidades na v0.1.0

* 🕺 **Seleção de Dançarino / Coach:** Para músicas multijogador, duetos, trios e quartetos (ex.: *Dragostea Din Tei*), escolha diretamente no seu celular o seu Coach (1 a 4) e navegue/confirme nos menus do jogo com um só toque.
* 🎯 **Ajuste Fino de Acelerômetro (1.20x Pulso):** Seletor de ganho cinemático de alta precisão (`1.0x`, `1.15x`, `1.20x ★`, `1.25x`, `1.30x`) para compensar o raio de rotação menor do pulso ($r_{\text{pulso}} < r_{\text{mão}}$) e alcançar facilmente pontuações Megastar (11.5k–13k).
* ⏸️ **Gesto de Pausa Calibrado:** Agora requer um **chacoalhar triplo deliberado a 3.7g (36 m/s²)** com cooldown de 3 segundos, eliminando pausas acidentais durante movimentos intensos de braço.
* 📊 **Sessões Sweat Contínuas (Sem Fragmentação):** As pausas apenas congelam o cronômetro e preservam a contagem de calorias sem encerrar a sessão nem poluir o histórico com entradas duplicadas. Finalização automática oficial ao receber a tela de resultados (*Recap*).
* 📳 **Feedback Háptico Imersivo no Smartwatch (Motor LRA):**
  * 🌟 **Gold Move ("YEAH!"):** Pulso duplo pesado e profundo (`[0, 150, 50, 250]` ms @ 200/255).
  * ⭐ **Desbloqueio de Estrelas (1–5, Superstar, Megastar):** Fanfarra crescente em pulso triplo (`[0, 80, 40, 80, 40, 140]` ms @ 120/180/255) para saber sua evolução sem olhar para a tela.
  * ✨ **Perfect Move:** Micro-clique seco e nítido (35 ms).
* 🏷️ **Reconhecimento Inteligente de Músicas:** Extração automática de títulos reais de músicas dos pacotes de rede, filtrando mensagens de tutoriais do jogo.

---

## 🏗️ Arquitetura do Sistema

```
[ Galaxy Watch 4 / Wear OS ] 
       │
       │ BLE / Wi-Fi (Google Play Services ChannelClient)
       │ Fluxo contínuo binário de 21 bytes Little-Endian (50 Hz exatos)
       ▼
[ Smartphone Android (Companion App) ]
       │
       │ Wi-Fi Local (WebSocket ws://<pc_ip>:8080/smartphone)
       │ Handshake oficial + pacotes JD_PhoneScoringData (~2 ms de latência)
       ▼
[ PC / Console (Just Dance 2017 / Legacy PC) ]
```

---

## 🚀 Requisitos

* **PC / Console:** Just Dance 2017 para PC ou servidor compatível na mesma rede Wi-Fi local.
* **Smartphone:** Android 8.0+ (Oreo ou superior).
* **Smartwatch:** Wear OS 3.0+ (Samsung Galaxy Watch 4, 5, 6, 7, Pixel Watch ou similar), preferencialmente no pulso direito.

---

## 🛠️ Compilação a partir do Código Fonte

O projeto utiliza Gradle e Kotlin 2.2 com Jetpack Compose no celular e Wear Compose no relógio:

```powershell
# Clonar o repositório
git clone https://github.com/Pipin333/jd-watch-controller.git
cd jd-watch-controller

# Compilar ambos os APKs (celular e relógio)
.\gradlew.bat assembleDebug
```

Os APKs gerados estarão localizados em:
* Relógio: `app/build/outputs/apk/debug/app-debug.apk`
* Celular: `myapplication/build/outputs/apk/debug/myapplication-debug.apk`

---

## 📲 Instalação Rápida via ADB Sem Fio

### 1. Ativar opções de desenvolvedor nos dois aparelhos
* No celular: *Configurações > Opções do desenvolvedor > Depuração sem fio*.
* No relógio: *Configurações > Opções do desenvolvedor > Depuração ADB + Depuração sem fio*.

### 2. Instalar via linha de comando:
```powershell
# Conectar via ADB Wi-Fi
adb connect <IP_CELULAR>:<PORTA>
adb connect <IP_RELOGIO>:<PORTA>

# Instalar app do celular
adb -s <IP_CELULAR>:<PORTA> install -r myapplication/build/outputs/apk/debug/myapplication-debug.apk

# Instalar app do relógio
adb -s <IP_RELOGIO>:<PORTA> install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🎮 Como Jogar

1. **Abra o jogo no PC** (*Just Dance 2017* ou *Legacy PC*) na tela de seleção de músicas.
2. **Abra o `JD Watch Controller` no celular:**
   * O aplicativo iniciará a busca automática na rede Wi-Fi local.
   * Quando o PC for encontrado, toque em **"CONECTAR"** (ou insira o IP manualmente caso a rede bloqueie UDP broadcast).
3. **Selecione o seu Dançarino:** Em músicas com múltiplos dançarinos, selecione `[ 🕺 Coach 1..4 ]` e confirme com `[ ✔ Confirmar ]`.
4. **Abra o `JD Watch Controller` no relógio:**
   * Toque em **"INICIAR STREAMING"**.
   * Você verá no celular a frequência em tempo real (50 Hz) e os batimentos cardíacos (BPM).
5. **Dance!** Escolha a música no PC. O celular alternará automaticamente para o painel de telemetria em tempo real, calculando calorias gastas ($Kcal$), vigor e vibrando a cada estrela e Gold Move.

---

## 🐍 Ferramentas de PC (`tools/`)

* **`tools/jd_haptic_bridge.py`:** Utilitário interativo em Python para testar e disparar vibrações hápticas no relógio com latência zero (<2 ms) via UDP (`:42069`):
  ```powershell
  python tools/jd_haptic_bridge.py --ip <IP_CELULAR>
  ```
* **`tools/inspect_jd.py`:** Inspetor de pacotes WebSocket e sniffer de rede do Just Dance.

---

## 📄 Licença

Projeto de código aberto sob a [Licença MIT](LICENSE). Desenvolvido para fins educacionais e de preservação da comunidade de Just Dance.
