package uy.edu.ort.battleshipdemo.servicios;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import uy.edu.ort.battleshipdemo.Modelo.Batalla;
import uy.edu.ort.battleshipdemo.Modelo.Oceano;
import uy.edu.ort.battleshipdemo.Modelo.Flota;
import uy.edu.ort.battleshipdemo.Modelo.Barco;
import uy.edu.ort.battleshipdemo.excepciones.BatallaException;
import uy.edu.ort.battleshipdemo.Modelo.Ronda;
import uy.edu.ort.battleshipdemo.Modelo.ConfiguracionBatalla;
import uy.edu.ort.battleshipdemo.Modelo.Tactica.Tactica;
import uy.edu.ort.battleshipdemo.Modelo.Eventos.ObservadorBatalla;
import uy.edu.ort.battleshipdemo.Modelo.Eventos.EventoBatalla;
import uy.edu.ort.battleshipdemo.Modelo.Jugador.JugadorHumano;
import uy.edu.ort.battleshipdemo.Modelo.Jugador.JugadorMaquina;

public class SistemaBatallas implements ObservadorBatalla {

    private List<Batalla> batallas;

    private List<ObservadorBatalla> observadores = new ArrayList<>();

    private static SistemaBatallas instancia;

    public static SistemaBatallas getInstancia() {
        if (instancia == null) {
            instancia = new SistemaBatallas();
        }
        return instancia;
    }

    private SistemaBatallas() {
        this.batallas = new ArrayList<>();
    }

    // Nati, letra dice que hay que generar nombre al azar si queda vacio.
    private String generarLugar() {
        String letras = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        Random azar = new Random(); // averigue como generar algo random y es asi.
        String nombreDelLugar = "";
        // intente usando for... pero no funciono
        // entonces usé do while, para ver si el nombre esta ocupado
        do {
            nombreDelLugar = "";
            // le pregunté a la IA como generar un nombre al azar.
            for (int i = 0; i < 6; i++) {
                int posicion = azar.nextInt(letras.length());
                nombreDelLugar += letras.charAt(posicion);
            }
        } while (buscarBatalla(nombreDelLugar) != null);
        return nombreDelLugar;
    }

    public void agregarBatalla(Batalla batalla) throws BatallaException {
        if (batalla == null) {
            throw new BatallaException("La batalla no puede ser nula");
        }
        String nombreLugar = batalla.getLugar();
        synchronized (batallas) {
            // Probando, me di cuenta que faltaba un trim()
            if (nombreLugar == null || nombreLugar.trim().isEmpty()) {
                // debido a que no puede ser vacio el nombre, vamos a poner generarLugar
                nombreLugar = generarLugar();
                batalla.setLugar(nombreLugar);
            }
            if (buscarBatalla(nombreLugar) != null) {
                throw new BatallaException("No se puede librar más de una batalla en el mismo lugar, cambie de nombre");
            }
            this.batallas.add(batalla);
            batalla.agregarObservador(this);
        }
        actualizar(new EventoBatalla(nombreLugar, "BATALLA_AGREGADA"));
    }

    public List<Batalla> getBatallas() {
        synchronized (batallas) {
            return new ArrayList<>(batallas);
        }
    }

    public Batalla buscarBatalla(String nombreLugar) {
        synchronized (batallas) {
            for (Batalla batalla : batallas) {
                if (batalla.getLugar().equals(nombreLugar)) {
                    return batalla;
                }
            }
            // no devuelvo excepcion, por que si el lugar esta disponible
            // no puedo tirar una excepcion batalla...
        }
        return null;
    }

    public void ubicarBarco(String lugarBatalla, int indiceBarco, int fila, int columna, boolean horizontal)
            throws BatallaException {
        Batalla batalla = buscarBatalla(lugarBatalla);
        if (batalla == null) {
            throw new BatallaException("La batalla indicada no existe.");
        }
        // hay que proteger la accion de ubicar barco, por que una pestaña puede enviar
        // un click mientras que la otra acaba de iniciar el combate.. deben estar en el
        // mismo
        // estado.
        synchronized (batalla) {
            if (batalla.getEstado().getEstado() != Batalla.Estados.enPreparacion) {
                throw new BatallaException(
                        "Solo se pueden ubicar barcos durante la preparación");
            }

            Oceano oceano = batalla.getJugadorHumano().getOceano();
            Flota flota = oceano.getFlota();
            Barco barco = flota.buscarUnBarcoPorSuIndice(indiceBarco);

            oceano.ubicarBarco(barco, fila, columna, horizontal);
            actualizar(new EventoBatalla(lugarBatalla, "BARCO_UBICADO"));
        }
    }

    // Esto lo vamos a usar para mostrar estadisticas de la campaña
    public int contarBatallasPorEstado(Batalla.Estados estadoBuscado) {
        int cantidad = 0;

        for (Batalla batalla : getBatallas()) {
            synchronized (batalla) {
                if (batalla.getEstado().getEstado() == estadoBuscado) {
                    cantidad++;
                }
            }
        }

        return cantidad;
    }

    public double calcularPorcentajeExito() {
        List<Batalla> listado = getBatallas();
        int total = listado.size();

        if (total == 0) {
            return 0.0;
        }

        int ganadas = 0;

        for (Batalla batalla : listado) {
            synchronized (batalla) {
                if (batalla.getEstado().getEstado() == Batalla.Estados.ganada) {
                    ganadas++;
                }
            }
        }

        return 100.0 * ganadas / total;
    }

    public void validarFlotaParaLibrarBatalla(String lugarBatalla) throws BatallaException {
        Batalla batalla = buscarBatalla(lugarBatalla);
        if (batalla == null) {
            throw new BatallaException("La batalla ingresada no existe");
        }
        Oceano oceano = batalla.getJugadorHumano().getOceano();
        if (!oceano.estanTodosUbicados()) {
            throw new BatallaException("Debe posicionar todos sus barcos primero");
        }
    }

    public void iniciarCombate(String lugarBatalla) throws BatallaException {
        Batalla batalla = buscarBatalla(lugarBatalla);

        if (batalla == null) {
            throw new BatallaException("La batalla indicada no existe");
        }

        synchronized (batalla) {
            batalla.iniciarCombate();
        }
    }

    public Ronda jugarRonda(
            String lugarBatalla, int fila, int columna, int numeroRondaEsperada)
            throws BatallaException {

        Batalla batalla = buscarBatalla(lugarBatalla);

        if (batalla == null) {
            throw new BatallaException("La batalla indicada no existe");
        }

        return batalla.jugarRonda(fila, columna, numeroRondaEsperada);
    }

    public void agregarObservador(
            String lugarBatalla, ObservadorBatalla observador)
            throws BatallaException {

        Batalla batalla = buscarBatalla(lugarBatalla);

        if (batalla == null) {
            throw new BatallaException("La batalla indicada no existe");
        }

        if (!observadores.contains(observador)) {
            observadores.add(observador);
        }
    }

    public void agregarObservadorSala(ObservadorBatalla observador) {
        if (!observadores.contains(observador)) {
            observadores.add(observador);
        }
    }

    public void quitarObservadorSala(ObservadorBatalla observador) {
        observadores.remove(observador);
    }

    public void quitarObservador(
            String lugarBatalla, ObservadorBatalla observador)
            throws BatallaException {

        Batalla batalla = buscarBatalla(lugarBatalla);

        if (batalla == null) {
            throw new BatallaException("La batalla indicada no existe");
        }

        observadores.remove(observador);
    }

    @Override
    public void actualizar(EventoBatalla evento) {
        List<ObservadorBatalla> copiaObservadores = new ArrayList<>(observadores);

        for (ObservadorBatalla observador : copiaObservadores) {
            try {
                observador.actualizar(evento);
            } catch (RuntimeException error) {
                // Una conexión fallida no debe interrumpir el aviso a las demás.
                observadores.remove(observador);
            }
        }
    }

    public Batalla crearBatalla(
            String lugar, ConfiguracionBatalla configuracion, Tactica tactica) throws BatallaException {

        // Empezamos con las validaciones!
        if (configuracion == null) {
            throw new BatallaException("La configuración es obligatoria");
        }

        configuracion.validar();

        if (tactica == null) {
            throw new BatallaException("Debe seleccionar una táctica");
        }
        // creamos una batalla usando el constructor vacio de la batalla
        Batalla batalla = new Batalla();
        // le colocamos el lugar recibido desde los parametros.
        batalla.setLugar(lugar);
        // y lo mismo vamos a hacer con la configuracion aca.
        batalla.setConfiguracion(configuracion);
        // Aca estamos creando el jugador humano
        JugadorHumano humano = new JugadorHumano("Jugador humano");
        humano.setOceano(configuracion.crearOceano());
        batalla.setJugadorHumano(humano);
        // luego el jugador maquina
        JugadorMaquina maquina = new JugadorMaquina("Máquina", tactica);
        maquina.setOceano(crearOceanoMaquina(configuracion));
        batalla.setJugadorMaquina(maquina);
        // luego agregamos la batalla.
        agregarBatalla(batalla);

        return batalla;
    }

    private Oceano crearOceanoMaquina(ConfiguracionBatalla configuracion)
            throws BatallaException {
        for (int intento = 0; intento < 100; intento++) {
            Oceano oceano = configuracion.crearOceano();
            boolean flotaUbicada = true;

            for (Barco barco : oceano.getFlota().getBarcos()) {
                if (!ubicarBarcoAleatoriamente(oceano, barco)) {
                    flotaUbicada = false;
                    break;
                }
            }

            if (flotaUbicada) {
                return oceano;
            }
        }
        throw new BatallaException("No se pudo ubicar la flota de la máquina. Probá otra configuración.");
    }

    private boolean ubicarBarcoAleatoriamente(Oceano oceano, Barco barco) {
        Random azar = new Random();
        int cantidadIntentos = oceano.getFilas() * oceano.getColumnas() * 2;

        for (int intento = 0; intento < cantidadIntentos; intento++) {
            int fila = azar.nextInt(oceano.getFilas());
            int columna = azar.nextInt(oceano.getColumnas());
            boolean horizontal = azar.nextBoolean();

            try {
                oceano.ubicarBarco(barco, fila, columna, horizontal);
                return true;
            } catch (BatallaException error) {
                // Esa posición no sirve; se prueba otra posición aleatoria.
            }
        }
        return false;
    }

}
