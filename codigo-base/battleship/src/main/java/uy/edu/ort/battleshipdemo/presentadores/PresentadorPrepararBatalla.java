package uy.edu.ort.battleshipdemo.presentadores;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Scope;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import uy.edu.ort.battleshipdemo.Commands;
import uy.edu.ort.battleshipdemo.Command;
import uy.edu.ort.battleshipdemo.InicializarDto;
import uy.edu.ort.battleshipdemo.ValorDto;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.io.IOException;

import uy.edu.ort.battleshipdemo.DTOs.TipoBarcoDto;
import uy.edu.ort.battleshipdemo.DTOs.TacticaDto;
import uy.edu.ort.battleshipdemo.DTOs.PrepararBatallaDto;
import uy.edu.ort.battleshipdemo.DTOs.CantidadBarcoDto;
import uy.edu.ort.battleshipdemo.DTOs.BarcoPreparacionDto;
import uy.edu.ort.battleshipdemo.DTOs.UbicarBarcoDto;
import uy.edu.ort.battleshipdemo.fachadas.Fachada;
import uy.edu.ort.battleshipdemo.Modelo.ConfiguracionBatalla;
import uy.edu.ort.battleshipdemo.Modelo.DetalleFlota;
import uy.edu.ort.battleshipdemo.Modelo.Flota;
import uy.edu.ort.battleshipdemo.Modelo.TipoBarco;
import uy.edu.ort.battleshipdemo.Modelo.Oceano;
import uy.edu.ort.battleshipdemo.Modelo.Batalla;
import uy.edu.ort.battleshipdemo.Modelo.Celda;
import uy.edu.ort.battleshipdemo.Modelo.Barco;
import uy.edu.ort.battleshipdemo.Modelo.Jugador.JugadorHumano;
import uy.edu.ort.battleshipdemo.Modelo.Jugador.JugadorMaquina;
import uy.edu.ort.battleshipdemo.Modelo.Tactica.Tactica;
import uy.edu.ort.battleshipdemo.Modelo.Tactica.TacticaSecuencial;
import uy.edu.ort.battleshipdemo.Modelo.Tactica.TacticaEspiral;
import uy.edu.ort.battleshipdemo.Modelo.Tactica.TacticaCazaYAtaque;
import uy.edu.ort.battleshipdemo.Modelo.Eventos.EventoBatalla;
import uy.edu.ort.battleshipdemo.Modelo.Eventos.ObservadorBatalla;
import uy.edu.ort.battleshipdemo.excepciones.BatallaException;

@RestController
@RequestMapping("/preparacion-batalla")
@Scope("session")
public class PresentadorPrepararBatalla {
    private Fachada fachada = Fachada.getInstancia();
    private Random azar = new Random();

    @PostMapping("/vistaConectada")
    public Commands vistaConectada() {
        return Commands.create(tiposBarco(), tacticas());
    }

    // fachada-getTiposBarco() obtiene los tipos del catalogo.
    // TipoBarcoDto.listaDeBarcosPreparadosDtos() convierte la lista de tipos de
    // barco a una lista de
    // DTOs.
    private Command tiposBarco() {
        return new Command(
                "tiposBarco",
                TipoBarcoDto.listaDtos(fachada.getTiposBarco()));
    }

    private Command tacticas() {
        List<Tactica> disponibles = new ArrayList<>();
        disponibles.add(new TacticaSecuencial());
        disponibles.add(new TacticaEspiral());
        disponibles.add(new TacticaCazaYAtaque());
        return new Command("tacticas", TacticaDto.listaTacticaDto(disponibles));
    }

    private Tactica crearTactica(String nombre) throws BatallaException {
        if ("Secuencial".equals(nombre)) {
            return new TacticaSecuencial();
        }
        if ("Espiral".equals(nombre)) {
            return new TacticaEspiral();
        }
        if ("Caza y ataque".equals(nombre)) {
            return new TacticaCazaYAtaque();
        }
        throw new BatallaException("Seleccioná una táctica de juego válida.");
    }

    private ConfiguracionBatalla crearConfiguracion(PrepararBatallaDto datos) throws BatallaException {
        // primero creo una instancia de ConfiguracionBatalla
        ConfiguracionBatalla configuracion = new ConfiguracionBatalla();
        List<DetalleFlota> listaDeFlotas = new ArrayList<>();
        // datos, tiene la cantidades de barcos, para armar la flota
        List<CantidadBarcoDto> cantidadesEnDto = datos.getCantidadesBarcos();
        // luego, uso las filas y columnas de los datos
        int filas = datos.getFilas();
        int columnas = datos.getColumnas();
        // y las uso para configurar todo.
        configuracion.setFilas(filas);
        configuracion.setColumnas(columnas);
        // despues, recorro las cantidades recibidas para construir los detalles de la
        // flota.
        for (CantidadBarcoDto cantidadDto : cantidadesEnDto) {
            TipoBarco tipoBarco = fachada.buscarTipoBarco(cantidadDto.getNombreTipo());
            DetalleFlota detalle = new DetalleFlota(tipoBarco, cantidadDto.getCantidad());
            listaDeFlotas.add(detalle);
        }
        configuracion.setFlota(listaDeFlotas);
        configuracion.validar();
        return configuracion;
    }

    private Command inicializarTablero(Oceano oceano) {
        int filas = oceano.getFilas();
        int columnas = oceano.getColumnas();
        InicializarDto datosDelTablero = new InicializarDto(filas, columnas, "/preparacion-batalla/ubicarBarco",
                "/preparacion-batalla/ubicarEnAguasEnemigas");
        return new Command("inicializar", datosDelTablero);

    }

    private Command barcosPreparacion(Flota flota) {
        return new Command("barcosPreparacion", BarcoPreparacionDto.listaDeBarcosPreparadosDtos(flota.getBarcos()));
    }

    // cada comando "mostrarAguasPropias indica fila y columna mostra el simbolo del
    // barco"
    // entonces precisamos commands para reunir todas las ordenes que precisa el
    // barco
    // el barco precisa 4 ordenes para 4 celdas.
    private Commands mostrarBarcosUbicados(Flota flota) {
        Commands comandos = Commands.create();
        for (Barco barco : flota.getBarcos()) {
            // Aca tengo que hacer 2 for... por que
            // no puedo aplicar esto dentro de barco y crear un command
            // desde dentro del barco.
            for (Celda celda : barco.getCeldas()) {
                ValorDto valorDto = new ValorDto(
                        celda.getFila(),
                        celda.getColumna(),
                        "barco");
                Command comando = new Command("mostrarAguasPropias", valorDto);
                comandos.add(comando);
            }
        }
        return comandos;
    }

    // Aca estamos conversando con crearBatalla mas que nada, ahi en Fachada tenemos
    // crearBatalla que direcciona con el modelo.
    @PostMapping("/prepararBatalla")
    public Commands prepararBatalla(PrepararBatallaDto prepararBatallaDto)
            throws BatallaException {

        ConfiguracionBatalla configuracion = crearConfiguracion(prepararBatallaDto);

        Tactica tactica = crearTactica(prepararBatallaDto.getNombreTactica());

        Batalla batalla = fachada.crearBatalla(
                prepararBatallaDto.getLugarBatalla(),
                configuracion,
                tactica);

        Commands comandos = abrirPreparacion(batalla.getLugar());
        comandos.add(new Command("batallaCreada", batalla.getLugar()));

        return comandos;
    }

    @PostMapping("/ubicarBarco")
    public Commands ubicarBarco(UbicarBarcoDto ubicarBarcoDto)
            throws BatallaException {
        // le mandamos a la fachada todos los datos del usuario para colocar el barco
        fachada.ubicarBarco(
                ubicarBarcoDto.getLugarDeLaBatalla(),
                ubicarBarcoDto.getIndiceDelBarco(),
                ubicarBarcoDto.getFila(),
                ubicarBarcoDto.getColumna(),
                ubicarBarcoDto.getEsHorizontal());
        // creamos el comando para el lugar de la batalla
        Commands comandos = abrirPreparacion(
                ubicarBarcoDto.getLugarDeLaBatalla());
        // cuando todo termine, decimos que el barco fue ubicado y lo ponemos con la
        // llave "barcoUbicado"
        comandos.add(new Command("barcoUbicado", "Barco ubicado!"));

        return comandos;
    }

    @PostMapping("/abrirPreparacion")
    public Commands abrirPreparacion(String lugarBatalla) throws BatallaException {
        // primero buscamos la batalla
        Batalla batalla = fachada.buscarBatalla(lugarBatalla);
        if (batalla == null) {
            throw new BatallaException("La batalla no existe");
        }
        // Aplicamos lo que uso el profe para sincronizar, sin esto otra solicitud
        // podría colocar
        // un barco entre consultas.
        synchronized (batalla) {
            // hay que consultar el estado actual para ver si esta en preparacion
            if (batalla.getEstado().getEstado() != Batalla.Estados.enPreparacion) {
                return Commands.create(
                        new Command("combateIniciado", batalla.getLugar()));
            }
            // obtenemos la batalla del jugador humano.
            Oceano oceano = batalla.getJugadorHumano().getOceano();
            // cuado tenemos el oceano, podemos acceder a la flota
            Flota flota = oceano.getFlota();
            Commands comandos = Commands.create();
            // Ahora creamos comandos, para lugarBatalla.
            comandos.add(new Command("lugarBatalla", batalla.getLugar()));
            comandos.add(inicializarTablero(oceano));
            comandos.add(barcosPreparacion(flota));
            Commands dibujarBarcos = mostrarBarcosUbicados(flota);
            // Aca incorporamos las ordenes del dibujo despues de inicializar el tablero.
            for (Command comando : dibujarBarcos.getCommands()) {
                comandos.add(comando);
            }
            // aca corroboramos que la flota se haya ubicado
            Command estadoFlota = new Command(
                    "flotaUbicada",
                    flota.estanTodosUbicados());
            comandos.add(estadoFlota);
            return comandos;
        }
    }

    @PostMapping("/ubicarEnAguasEnemigas")
    public Commands ubicarEnAguasEnemigas() throws BatallaException {
        throw new BatallaException("No puede ubicar barcos en aguas enemigas");
    }

    @PostMapping("/validarFlotaParaLibrarBatalla")
    public Commands validarFlotaParaLibrarBatalla(String lugarBatalla) throws BatallaException {
        fachada.validarFlotaParaLibrarBatalla(lugarBatalla);
        fachada.iniciarCombate(lugarBatalla);
        return Commands.create(
                new Command("combateIniciado", lugarBatalla));
    }

    @GetMapping(value = "/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter eventos(String lugarBatalla) throws BatallaException {
        SseEmitter emisor = new SseEmitter();
        ObservadorBatalla observador = new ObservadorBatalla() {
            @Override
            public void actualizar(EventoBatalla evento) {
                if (evento.getIdentificadorBatalla().equals(lugarBatalla)) {
                    try {
                        emisor.send(
                                SseEmitter.event()
                                        .name("actualizacion")
                                        .data(evento.getTipoCambio()));
                    } catch (IOException error) {
                        emisor.completeWithError(error);
                    }
                }
            }
        };
        fachada.agregarObservador(lugarBatalla, observador);
        // Esto que tenemos aca sirve para cuando vence la conexion por tiempo, cerrar
        // el emisor.
        // la flechita () -> significa que es una funcion que ocurrira cuando se corra
        // el evento.
        emisor.onCompletion(() -> quitarObservador(lugarBatalla, observador));

        emisor.onTimeout(() -> {
            quitarObservador(lugarBatalla, observador);
            emisor.complete();
        });

        emisor.onError(error -> quitarObservador(lugarBatalla, observador));
        return emisor;
    }

    // este metodo nos permitira retirar la suscripcion cuando termine la conexion
    // del emisor (SSE)
    private void quitarObservador(
            String lugarBatalla, ObservadorBatalla observador) {

        try {
            fachada.quitarObservador(lugarBatalla, observador);
        } catch (BatallaException error) {
            // Si la batalla ya no existe, no hay una suscripción que conservar.
        }
    }

}
